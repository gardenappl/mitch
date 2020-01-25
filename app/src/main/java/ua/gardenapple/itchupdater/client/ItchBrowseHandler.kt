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
import ua.gardenapple.itchupdater.installer.DownloadStartListener

class ItchBrowseHandler(val context: Context, val coroutineScope: CoroutineScope): DownloadStartListener {

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
                val job1 =  async {
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
            tryStartDownload()
        }
    }

    fun setClickedUploadId(uploadId: Int) {
        Log.d(LOGGING_TAG, "Set upload ID: $uploadId")
        clickedUploadId = uploadId
        tryStartDownload()
    }

    override suspend fun onDownloadStarted(downloadId: Long) {
        currentDownloadId = downloadId
        tryStartDownload()
    }

    private fun tryStartDownload() {
        Log.d(LOGGING_TAG, "Game ID: $lastDownloadGameId")
        Log.d(LOGGING_TAG, "Upload ID: $clickedUploadId")
        Log.d(LOGGING_TAG, "Download ID: $currentDownloadId")
        Log.d(LOGGING_TAG, "Download page URL: $lastDownloadPageUrl")

        val doc = lastDownloadDoc ?: return
        val gameId = lastDownloadGameId ?: return
        val uploadId = clickedUploadId ?: return
        val downloadId = currentDownloadId ?: return
        val downloadPageUrl = lastDownloadPageUrl ?: return

        clickedUploadId = null
        currentDownloadId = null

        coroutineScope.launch(Dispatchers.IO) {
            Log.d(LOGGING_TAG, "Handling download...")

            val db = AppDatabase.getDatabase(context)
            val downloadManager = context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager

            var game = db.gameDao.getGameById(gameId)
            if(game == null) {
                val storeUrl = ItchWebsiteParser.getStoreUrlFromDownloadPage(downloadPageUrl)
                Log.d(LOGGING_TAG, "Game is null! Fetching $storeUrl...")
                val doc = ItchWebsiteUtils.fetchAndParseDocument(storeUrl)
                game = ItchWebsiteParser.getGameInfo(doc, storeUrl)
                db.gameDao.upsert(game)
            }

            val uploads = ItchWebsiteParser.getUploads(gameId, doc, setPending = true)
            var installation = db.installDao.findPendingInstallation(gameId)
            if(installation != null) {
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
            db.uploadDao.insert(uploads)
            db.installDao.insert(installation)
        }
    }
}