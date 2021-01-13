package ua.gardenapple.itchupdater.client

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.IOException


class ItchAccessDeniedException(message: String) : Exception(message)

class GameDownloader {
    companion object {
        private const val LOGGING_TAG = "GameDownloader"

        /**
         * Has a chance to fail if the user does not have access, or if the uploadId is no longer available
         * (in which case it will perform another update check)
         * @return true if user has access to uploadId
         */
        suspend fun startUpdate(context: Context, update: UpdateCheckResult): Boolean {
            val uploadId = update.uploadID!!
            val game = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val install = db.installDao.getInstallationById(update.installationId)

                db.gameDao.getGameById(install!!.gameId)!!
            }

            val fileRequestUrl = Uri.parse(game.storeUrl).buildUpon().run {
                appendPath("file")
                appendPath(uploadId.toString())
                update.downloadPageUrl?.downloadKey?.let {
                    appendQueryParameter("key", it)
                }
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
                Mitch.httpClient.newCall(request).execute().use { response ->
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
                Mitch.httpClient.newCall(request).execute().use { response ->
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

            val pendingInstall = try {
                ItchWebsiteParser.getPendingInstallation(doc, uploadId)
            } catch (e: ItchWebsiteParser.UploadNotFoundException) {
                Log.d(LOGGING_TAG, "Required upload not found, requesting update check...")
                val updateCheckRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .build()

                WorkManager.getInstance(context).enqueue(updateCheckRequest)
                return false
            }

            requestDownload(context, pendingInstall, downloadUrl, contentDisposition, mimeType)
            return true
        }

        /**
         * Direct request to start downloading file into internal storage.
         * Once download (and/or installation) is complete, the files for [replacedUploadId] will be deleted.
         * @param pendingInstall the [Installation] which we're about to download. Must have internalId = 0
         * @param url URL of file to download
         * @param contentDisposition HTTP content disposition header
         * @param mimeType MIME type
         * @param replacedUploadId upload to delete
         */
        suspend fun requestDownload(
            context: Context,
            pendingInstall: Installation,
            url: String,
            contentDisposition: String?,
            mimeType: String?,
            replacedUploadId: Int = pendingInstall.uploadId
        ) = withContext(Dispatchers.IO) {

            if (pendingInstall.internalId != 0)
                throw IllegalArgumentException("Pending installation already has internalId!")

            val uploadId = pendingInstall.uploadId
            val fileName = if (mimeType == "application/octet-stream") {
                URLUtil.guessFileName(url, contentDisposition, null)
            } else {
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            }

            val db = AppDatabase.getDatabase(context)
            val downloadFileManager = Mitch.fileManager
            var willStartDownload = false

            //cancel download for current pending installation
            db.installDao.getPendingInstallation(uploadId)?.let { currentPendingInstall ->
                Log.d(LOGGING_TAG, "Already existing install for $uploadId")

                if (currentPendingInstall.status == Installation.STATUS_DOWNLOADING) {
                    willStartDownload = true
                    downloadFileManager.requestCancellation(currentPendingInstall.downloadOrInstallId!!,
                        uploadId) {

                        runBlocking(Dispatchers.IO) {
                            db.installDao.delete(currentPendingInstall.internalId)
                            downloadFileManager.startDownload(url, fileName, pendingInstall,
                                replacedUploadId)
                        }
                    }
                }
            }

            if (!willStartDownload) {
                downloadFileManager.startDownload(url, fileName, pendingInstall, replacedUploadId)
            }
        }
    }
}