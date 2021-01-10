package ua.gardenapple.itchupdater.client

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.DownloadFileManager
import java.io.IOException

class GameDownloader(val context: Context) {
    companion object {
        private const val LOGGING_TAG = "GameDownloader"
    }

    class ItchAccessDeniedException(message: String) : Exception(message)

    /**
     * Has a chance to fail if the user does not have access, or if the uploadId is no longer available
     * (in which case it will perform another update check)
     * @return true if user has access to uploadId
     */
    suspend fun startDownload(game: Game, uploadId: Int, downloadKey: String?): Boolean {
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
        val result: String = withContext(Dispatchers.IO) {
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
            val storePageDoc = ItchWebsiteUtils.fetchAndParse(game.storeUrl)
            val downloadPageUrl =
                ItchWebsiteParser.getDownloadUrl(storePageDoc, game.storeUrl)
            if (downloadPageUrl == null)
                throw ItchAccessDeniedException("Can't access download page for ${game.name}")
            downloadPageUrl.url
        }
        val doc = ItchWebsiteUtils.fetchAndParse(url)

        //Temporary download ID
        val pendingInstall = try {
            ItchWebsiteParser.getPendingInstallation(doc, uploadId, 0)
        } catch (e: ItchWebsiteParser.UploadNotFoundException) {
            Log.d(LOGGING_TAG, "Required upload not found, requesting update check...")
            val updateCheckRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .build()

            WorkManager.getInstance(context).enqueue(updateCheckRequest)
            return false
        }

        MitchApp.downloadFileManager.requestDownload(uploadId, downloadUrl, contentDisposition, mimeType) {
                downloadId ->
            pendingInstall.downloadOrInstallId = downloadId
            runBlocking(Dispatchers.IO) {
                updateDatabase(url, pendingInstall)
            }
        }
        return true
    }

    /**
     * Note: [startDownload] updates the database on its own. Use this method only if you're
     * handling downloads in some other way.
     */
    suspend fun updateDatabase(
        downloadPageUrl: String?,
        pendingInstall: Installation
    ) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "Updating database based on download...")

        var game = db.gameDao.getGameById(pendingInstall.gameId)
        if (game == null) {
            val storeUrl = ItchWebsiteParser.getStoreUrlFromDownloadPage(Uri.parse(downloadPageUrl!!))
            Log.d(LOGGING_TAG, "Game is null! Fetching $storeUrl...")
            val storeDoc = ItchWebsiteUtils.fetchAndParse(storeUrl)
            game = ItchWebsiteParser.getGameInfoForStorePage(storeDoc, storeUrl)
            db.gameDao.upsert(game)
        }

        //Cancel download for the same uploadId
        val installation = db.installDao.getPendingInstallation(pendingInstall.uploadId)
        if (installation != null) {
            installation.downloadOrInstallId?.let { MitchApp.fetch.cancel(it.toInt()) }
        }
        db.installDao.insert(pendingInstall)
    }
}