package ua.gardenapple.itchupdater.client

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.ui.BrowseFragment

class ItchBrowseHandler(val context: Context) {

    companion object {
        const val LOGGING_TAG = "ItchBrowseHandler"
    }

    private lateinit var lastDownloadDoc: Document
    private var lastDownloadGameId: Int = -1

    suspend fun onPageVisited(doc: Document, url: String) {
        if(ItchWebsiteUtils.isStorePage(doc)) {

            val db = AppDatabase.getDatabase(context)

            withContext(Dispatchers.IO) {
                val job1 =  async {
                    if (ItchWebsiteUtils.isStorePage(doc)) {
                        val game = ItchWebsiteParser.getGameInfo(doc, url)

                        withContext(Dispatchers.IO) {
                            Log.d(LOGGING_TAG, "Adding game $game")
                            db.gameDao.insert(game)
                        }
                    }
                }
                job1.await()
            }
        }
        else if(ItchWebsiteUtils.isDownloadPage(doc)) {
            lastDownloadDoc = doc
            lastDownloadGameId = ItchWebsiteUtils.getGameId(doc)
        }
    }

    suspend fun onGameDownloadStarted(uploadId: Int) {
        val uploads = ItchWebsiteParser.getAndroidUploads(lastDownloadGameId, lastDownloadDoc)

        //TODO: PendingUpload
    }
}