package ua.gardenapple.itchupdater.client

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.data.JusticeBundleGameIDs
import ua.gardenapple.itchupdater.data.PalestineBundleGameIDs
import ua.gardenapple.itchupdater.database.AppDatabase

class ItchBrowseHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val LOGGING_TAG = "ItchBrowseHandler"

        // ItchBrowseHandler may be re-created on fragment re-attachment,
        // but I want these values to be retained. Making them static is a lazy solution.
        @Volatile
        private var lastDownloadDoc: Document? = null
        @Volatile
        private var lastDownloadPageUrl: String? = null
        @Volatile
        private var clickedUploadId: Int? = null
        @Volatile
        private var currentDownloadUrl: String? = null
        @Volatile
        private var currentDownloadContentDisposition: String? = null
        @Volatile
        private var currentDownloadMimeType: String? = null
    }

    suspend fun onPageVisited(doc: Document, url: String) {
        lastDownloadDoc = null
        lastDownloadPageUrl = null

        if (ItchWebsiteUtils.isStorePage(doc)) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                ItchWebsiteParser.getGameInfoForStorePage(doc, url)?.let { game ->
                    Log.d(LOGGING_TAG, "Adding game $game")
                    db.gameDao.upsert(game)

                    if (JusticeBundleGameIDs.belongsToJusticeBundle(game.gameId)) {
                        Log.d(LOGGING_TAG, "Belongs to Racial Justice bundle!")
                        val username = ItchWebsiteUtils.getLoggedInUserName(doc)
                        val bundleLink = SpecialBundleHandler.getLinkForUser(context, false, username)
                        Log.d(LOGGING_TAG, "Bundle link: $bundleLink")
                    }
                    if (PalestineBundleGameIDs.belongsToPalestineBundle(game.gameId)) {
                        Log.d(LOGGING_TAG, "Belongs to Palestinian Aid bundle!")
                        val username = ItchWebsiteUtils.getLoggedInUserName(doc)
                        val bundleLink = SpecialBundleHandler.getLinkForUser(context, true, username)
                        Log.d(LOGGING_TAG, "Bundle link: $bundleLink")
                    }
                }
            }
        }
        if (ItchWebsiteUtils.hasGameDownloadLinks(doc)) {
            lastDownloadDoc = doc
            lastDownloadPageUrl = url
            tryStartDownload()
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
        if (SpecialBundleHandler.checkIsBundleLink(context, doc, url)) {
            Log.d(LOGGING_TAG, "Is bundle link! $url")
        }
    }

    suspend fun setClickedUploadId(uploadId: Int) = withContext(Dispatchers.IO) {
        Log.d(LOGGING_TAG, "Set upload ID: $uploadId")
        clickedUploadId = uploadId
        tryStartDownload()
    }

    suspend fun onDownloadStarted(url: String, contentDisposition: String?, mimeType: String?) = withContext(Dispatchers.IO) {
        currentDownloadUrl = url
        currentDownloadContentDisposition = contentDisposition
        currentDownloadMimeType = mimeType
        tryStartDownload()
    }

    private fun tryStartDownload() {
        Log.d(LOGGING_TAG, "Upload ID: $clickedUploadId")

        val downloadPageDoc = lastDownloadDoc ?: return
        val downloadPageUrl = lastDownloadPageUrl ?: return
        val uploadId = clickedUploadId ?: return
        val downloadUrl = currentDownloadUrl ?: return
        val contentDisposition = currentDownloadContentDisposition ?: return
        val mimeType = currentDownloadMimeType ?: return

        coroutineScope.launch(Dispatchers.IO) {
            val pendingInstall = ItchWebsiteParser.getPendingInstallation(downloadPageDoc, uploadId)

            coroutineScope.launch(Dispatchers.Main) {
                Toast.makeText(context, R.string.popup_download_started, Toast.LENGTH_LONG)
                    .show()
            }

            GameDownloader.requestDownload(context, pendingInstall, downloadUrl, downloadPageUrl,
                contentDisposition, mimeType)
        }

        clickedUploadId = null
        currentDownloadUrl = null
        currentDownloadMimeType = null
        currentDownloadContentDisposition = null
    }
}