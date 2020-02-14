package ua.gardenapple.itchupdater.client

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.work.Logger
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.upload.Upload
import ua.gardenapple.itchupdater.installer.DownloadRequester
import java.io.IOException

class WebGameDownloader(val context: Context) {
    companion object {
        const val LOGGING_TAG = "WebGameDownloader"
    }

    class ItchAccessDeniedException(message: String) : Exception(message)

    /**
     * Has a chance to fail if the user does not have access, or if the uploadId is no longer available
     * (in which case it will perform another update check)
     */
    suspend fun startDownload(game: Game, uploadId: Int, downloadKey: String?) {
        val fileRequestUrl = Uri.parse(game.storeUrl).buildUpon().run {
            appendPath("file")
            appendPath(uploadId.toString())
            if (downloadKey != null)
                appendQueryParameter("key", downloadKey)
            build()
        }
        Log.d(LOGGING_TAG, "File request URL: $fileRequestUrl")

        val form = FormBody.Builder().run {
            add("csrf_token", "Mitch-automated")
            build()
        }
        var request = Request.Builder().run {
            url(fileRequestUrl.toString())
            addHeader("Cookie", CookieManager.getInstance().getCookie(game.storeUrl))
            post(form)
            build()
        }
        var result: String = withContext(Dispatchers.IO) {
            MitchApp.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw IOException("Unexpected code $response")

                //Log.d(LOGGING_TAG, "Response: ${response.body!!.string()}")
                response.body!!.string()
            }
        }

        val jsonObject = JSONObject(result)
        val downloadUrl = jsonObject.getString("url")

        Log.d(LOGGING_TAG, "Download URL: $downloadUrl")

        request = Request.Builder().run {
            url(downloadUrl)
            get()
            build()
        }

        var mimeType: String? = null
        var contentDisposition: String? = null

        withContext(Dispatchers.IO) {
            MitchApp.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw IOException("Unexpected response $response")

                mimeType = response.header("Content-Type")?.split(';')!![0]
                contentDisposition = response.header("Content-Disposition")
            }
        }

        val url: String = if (game.downloadPageUrl != null) {
            game.downloadPageUrl
        } else {
            val storePageDoc = ItchWebsiteUtils.fetchAndParseDocument(game.storeUrl)
            val downloadPageUrl =
                ItchWebsiteParser.getDownloadUrlFromStorePage(storePageDoc, game.storeUrl, true)
            if (downloadPageUrl == null)
                throw ItchAccessDeniedException("Can't access download page for ${game.name}")
            downloadPageUrl.url
        }
        val doc = ItchWebsiteUtils.fetchAndParseDocument(url)

        val pendingUploads = ItchWebsiteParser.getUploads(game.gameId, doc, true)
        if(pendingUploads.find { upload -> upload.uploadId == uploadId } == null) {
            Log.d(LOGGING_TAG, "Required upload not found, requesting update check...")
            val updateCheckRequest = OneTimeWorkRequestBuilder<WebUpdateCheckWorker>()
                .build()

            WorkManager.getInstance(context).enqueue(updateCheckRequest)
            return
        }

        DownloadRequester.requestDownload(context, null, downloadUrl, contentDisposition, mimeType) {
            downloadId: Long -> runBlocking(Dispatchers.IO) {
                updateDatabase(game.gameId, uploadId, downloadId, url, pendingUploads)
            }
        }
    }

    /**
     * Note: [startDownload] updates the database on its own. Use this method only if you're
     * handling downloads in some other way.
     */
    suspend fun updateDatabase(
        gameId: Int,
        uploadId: Int,
        downloadId: Long,
        downloadPageUrl: String,
        pendingUploads: List<Upload>
    ) {
        val db = AppDatabase.getDatabase(context)
        Log.d(ItchBrowseHandler.LOGGING_TAG, "Handling download...")
        val downloadManager = context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager

        var game = db.gameDao.getGameById(gameId)
        if (game == null) {
            val storeUrl = ItchWebsiteParser.getStoreUrlFromDownloadPage(downloadPageUrl)
            Log.d(ItchBrowseHandler.LOGGING_TAG, "Game is null! Fetching $storeUrl...")
            val storeDoc = ItchWebsiteUtils.fetchAndParseDocument(storeUrl)
            game = ItchWebsiteParser.getGameInfo(storeDoc, storeUrl)
            db.gameDao.upsert(game)
        }

        var installation = db.installDao.findPendingInstallation(gameId)
        if (installation != null) {
            installation.downloadOrInstallId?.let { downloadManager.remove(it) }
            db.installDao.delete(installation.internalId)
        }
        installation = Installation(
            uploadId = uploadId,
            gameId = gameId,
            downloadOrInstallId = downloadId,
            status = Installation.STATUS_DOWNLOADING
        )
        db.uploadDao.clearPendingUploadsForGame(gameId)
        db.uploadDao.insert(pendingUploads)
        db.installDao.insert(installation)
    }
}