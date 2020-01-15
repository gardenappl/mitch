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

    suspend fun processItchData(doc: Document, url: String) {
        if(ItchWebsiteUtils.isDownloadPage(doc) || ItchWebsiteUtils.isStorePage(doc)) {

            val db = AppDatabase.getDatabase(context)

            withContext(Dispatchers.IO) {
                val job1 = async {
                    val gameId = ItchWebsiteUtils.getGameId(doc)
                    val uploads = ItchWebsiteParser.getAndroidUploads(gameId, doc)
                    for (upload in uploads) {
                        Log.d(LOGGING_TAG, "Adding upload $upload")
                    }
                    //TODO: actually check if these uploads should be saved
                    db.uploadDao().clearUploadsForGame(gameId)
                    db.uploadDao().insert(uploads)
                }

                val job2 = async {
                    if (ItchWebsiteUtils.isStorePage(doc)) {
                        val game = ItchWebsiteParser.getGameInfo(doc, url)

                        withContext(Dispatchers.IO) {
                            Log.d(LOGGING_TAG, "Adding game $game")
                            db.gameDao().insert(game)
                        }
                    }
                }
                job1.await()
                job2.await()
            }
        }
    }
}