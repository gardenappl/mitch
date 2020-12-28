package ua.gardenapple.itchupdater.client

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.AppDatabase

class ItchBrowseHandler(private val context: Context, private val coroutineScope: CoroutineScope) {

    companion object {
        private const val LOGGING_TAG = "ItchBrowseHandler"

        // ItchBrowseHandler may be re-created on fragment re-attachment,
        // but I want these values to be retained. Making them static is a lazy solution.
        private var lastDownloadDoc: Document? = null
        private var lastDownloadPageUrl: String? = null
        private var clickedUploadId: Int? = null
        private var currentDownloadId: Long? = null
    }

    suspend fun onPageVisited(doc: Document, url: String) {
        lastDownloadDoc = null
        lastDownloadPageUrl = null

        if (ItchWebsiteUtils.isStorePage(doc)) {
            val db = AppDatabase.getDatabase(context)
            val game = ItchWebsiteParser.getGameInfoForStorePage(doc, url)

            withContext(Dispatchers.IO) {
                Log.d(LOGGING_TAG, "Adding game $game")
                db.gameDao.upsert(game)
            }
        }
        if (ItchWebsiteUtils.hasGameDownloadLinks(doc)) {
            lastDownloadDoc = doc
            lastDownloadPageUrl = url
            tryUpdateDatabase()
        }
        if (!ItchWebsiteUtils.isStylizedPage(doc)) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().also {
                if (ItchWebsiteUtils.isDarkTheme(doc))
                    it.putString("current_site_theme", "dark")
                else
                    it.putString("current_site_theme", "light")
                it.apply()
            }
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
        Log.d(LOGGING_TAG, "Upload ID: $clickedUploadId")
        Log.d(LOGGING_TAG, "Download ID: $currentDownloadId")
        Log.d(LOGGING_TAG, "Download page URL: $lastDownloadPageUrl")

        val downloadPageDoc = lastDownloadDoc ?: return
        val uploadId = clickedUploadId ?: return
        val downloadId = currentDownloadId ?: return
        val downloadPageUrl = lastDownloadPageUrl ?: return

        clickedUploadId = null
        currentDownloadId = null

        coroutineScope.launch(Dispatchers.IO) {
            val downloader = GameDownloader(context)
            val pendingInstall =
                ItchWebsiteParser.getPendingInstallation(downloadPageDoc, uploadId, downloadId)
            downloader.updateDatabase(downloadPageUrl, pendingInstall)
        }
    }
}