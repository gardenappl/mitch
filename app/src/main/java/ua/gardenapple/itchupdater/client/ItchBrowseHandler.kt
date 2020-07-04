package ua.gardenapple.itchupdater.client

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation

class ItchBrowseHandler(val context: Context, val coroutineScope: CoroutineScope) {

    companion object {
        const val LOGGING_TAG = "ItchBrowseHandler"
    }

    private var lastDownloadDoc: Document? = null
    private var lastDownloadGameId: Int? = null
    private var lastDownloadPageUrl: String? = null
    private var clickedUploadId: Int? = null
    private var currentDownloadId: Long? = null

    suspend fun onPageVisited(doc: Document, url: String) {
        lastDownloadDoc = null
        lastDownloadGameId = null
        lastDownloadPageUrl = null

        if(ItchWebsiteUtils.isStorePage(doc)) {
            val db = AppDatabase.getDatabase(context)

            withContext(Dispatchers.IO) {
                val job1 = async {
                    if (ItchWebsiteUtils.isStorePage(doc)) {
                        val game = ItchWebsiteParser.getGameInfo(doc, url)

                        withContext(Dispatchers.IO) {
                            Log.d(LOGGING_TAG, "Adding game $game")
                            db.gameDao.upsert(game)
                        }
                    }
                }
                job1.await()
            }
        }
        if(ItchWebsiteUtils.hasGameDownloadLinks(doc)) {
            lastDownloadDoc = doc
            lastDownloadGameId = ItchWebsiteUtils.getGameId(doc)
            lastDownloadPageUrl = url
            tryUpdateDatabase()
        }
    }

    fun setClickedUploadId(uploadId: Int) {
        Log.d(LOGGING_TAG, "Set upload ID: $uploadId")
        clickedUploadId = uploadId
        tryUpdateDatabase()
    }

    fun onDownloadStarted(downloadId: Long) {
        currentDownloadId = downloadId
        tryUpdateDatabase()
    }

    private fun tryUpdateDatabase() {
        Log.d(LOGGING_TAG, "Game ID: $lastDownloadGameId")
        Log.d(LOGGING_TAG, "Upload ID: $clickedUploadId")
        Log.d(LOGGING_TAG, "Download ID: $currentDownloadId")
        Log.d(LOGGING_TAG, "Download page URL: $lastDownloadPageUrl")

        val downloadPageDoc = lastDownloadDoc ?: return
        val gameId = lastDownloadGameId ?: return
        val uploadId = clickedUploadId ?: return
        val downloadId = currentDownloadId ?: return
        val downloadPageUrl = lastDownloadPageUrl ?: return

        clickedUploadId = null
        currentDownloadId = null

        coroutineScope.launch(Dispatchers.IO) {
            val downloader = WebGameDownloader(context)
            val pendingUploads = ItchWebsiteParser.getUploads(gameId, downloadPageDoc, true)
            downloader.updateDatabase(gameId, uploadId, downloadId, downloadPageUrl, pendingUploads)
        }
    }
}