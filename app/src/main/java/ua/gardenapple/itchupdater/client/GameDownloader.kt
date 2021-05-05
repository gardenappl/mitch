package ua.gardenapple.itchupdater.client

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
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
         * (in which case it might perform another update check)
         */
        suspend fun startUpdate(context: Context, update: UpdateCheckResult) {
            when (doUpdate(context, update)) {
                UpdateCheckResult.EMPTY,
                UpdateCheckResult.ACCESS_DENIED ->
                    UpdateChecker(context).checkUpdates()
            }
        }

        /**
         * Has a chance to fail if the user does not have access, or if the uploadId is no longer available
         * @return [UpdateCheckResult.EMPTY] if uploadId is not found, otherwise [UpdateCheckResult.ACCESS_DENIED] or [UpdateCheckResult.UPDATE_AVAILABLE]
         */
        private suspend fun doUpdate(context: Context, update: UpdateCheckResult): Int {
            update.isInstalling = true
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                db.updateCheckDao.insert(update)
            }

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
                CookieManager.getInstance()?.getCookie(game.storeUrl)?.let { cookie ->
                    addHeader("Cookie", cookie)
                }
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
            Log.d(LOGGING_TAG, jsonObject.toString())
            if (!jsonObject.has("url"))
                return UpdateCheckResult.EMPTY

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

            val url: String
            if (game.downloadPageUrl != null) {
                url = game.downloadPageUrl
            } else {
                val storePageDoc = ItchWebsiteUtils.fetchAndParse(game.storeUrl)
                url = ItchWebsiteParser.getDownloadUrl(storePageDoc, game.storeUrl)?.url
                    ?: return UpdateCheckResult.ACCESS_DENIED.also {
                        Log.i(LOGGING_TAG, "Access denied to downloadUrl for ${game.storeUrl}")
                    }
            }

            val doc = ItchWebsiteUtils.fetchAndParse(url)

            val pendingInstall = try {
                ItchWebsiteParser.getPendingInstallation(doc, uploadId)
            } catch (e: ItchWebsiteParser.UploadNotFoundException) {
                return UpdateCheckResult.EMPTY
            }

            requestDownload(context, pendingInstall, downloadUrl, url, contentDisposition, mimeType)
            return UpdateCheckResult.UPDATE_AVAILABLE
        }

        /**
         * Direct request to start downloading file into internal storage.
         * Once download (and/or installation) is complete, the files not in [Installation.availableUploadIds] will be deleted.
         * @param pendingInstall the [Installation] which we're about to download. Must have internalId = 0
         * @param url URL of file to download
         * @param contentDisposition HTTP content disposition header
         * @param mimeType MIME type
         */
        suspend fun requestDownload(
            context: Context,
            pendingInstall: Installation,
            url: String,
            downloadPageUrl: String,
            contentDisposition: String?,
            mimeType: String?
        ) = withContext(Dispatchers.IO) {
            if (pendingInstall.internalId != 0)
                throw IllegalArgumentException("Pending installation already has internalId!")

            Log.d(LOGGING_TAG, "Downloading, pending install: $pendingInstall")

            //Make sure that the corresponding Game is present in the database
            val db = AppDatabase.getDatabase(context)

            var game = db.gameDao.getGameById(pendingInstall.gameId)
            if (game == null) {
                val storePageUrl = ItchWebsiteParser.getStoreUrlFromDownloadPage(Uri.parse(downloadPageUrl))
                val doc = ItchWebsiteUtils.fetchAndParse(storePageUrl)
                game = ItchWebsiteParser.getGameInfoForStorePage(doc, storePageUrl)!!
                Log.d(LOGGING_TAG, "Game is missing! Adding game $game")
                db.gameDao.upsert(game)
            }

            val fileName = if (mimeType == "application/octet-stream") {
                URLUtil.guessFileName(url, contentDisposition, null)
            } else {
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            }

            Mitch.fileManager.requestDownload(context, url, fileName, pendingInstall)
        }
    }
}