package ua.gardenapple.itchupdater.client

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload

class WebUpdateChecker {
    companion object {
        const val LOGGING_TAG: String = "WebUpdateChecker"
    }

    suspend fun checkUpdates(context: Context, gameId: Int): UpdateCheckResult = coroutineScope {
        val gameDao = AppDatabase.getDatabase(context).gameDao()

        val game: Game = withContext(Dispatchers.IO) {
            gameDao.getGameById(gameId)
        }

        val doc: Document = fetchDownloadPage(game)


        UpdateCheckResult.UNKNOWN
    }

    suspend fun fetchDownloadPage(game: Game): Document {
        val connectUrl = game.downloadPageUrl ?: game.storeUrl

        return Jsoup.connect(connectUrl).run {
            header("Cookie", CookieManager.getInstance().getCookie(connectUrl))
            withContext(Dispatchers.IO) {
                get()
            }
        }
    }
}