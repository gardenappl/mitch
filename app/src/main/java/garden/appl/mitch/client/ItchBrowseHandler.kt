package garden.appl.mitch.client

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.PREF_LANG_SITE_LOCALE
import garden.appl.mitch.R
import garden.appl.mitch.data.SpecialBundle
import garden.appl.mitch.data.containsGame
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game

class ItchBrowseHandler(private val context: Context) {
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
        @Volatile
        private var currentDownloadContentLength: Long? = null
    }

    data class Info(
        val specialBundle: SpecialBundle?,
        val bundleDownloadLink: String?,
        val game: Game?,
        val purchasedInfo: List<ItchWebsiteParser.PurchasedInfo>,
        val paymentInfo: ItchWebsiteParser.PaymentInfo?,
        val hasAndroidVersion: Boolean,
        val hasWindowsMacOrLinuxVersion: Boolean,
        val webLaunchLabel: String? = null,
        val isRunningCachedWebGame: Boolean = false,
        val isCachedWebGameOffline: Boolean = false
    )

    suspend fun onPageVisited(doc: Document, url: String): Info {
        lastDownloadDoc = null
        lastDownloadPageUrl = null

        var game: Game? = null
        var specialBundle: SpecialBundle? = null
        var bundleLink: String? = null
        var purchasedInfo: List<ItchWebsiteParser.PurchasedInfo> = emptyList()
        var paymentInfo: ItchWebsiteParser.PaymentInfo? = null
        var hasAndroidVersion = false
        var hasWindowsMacLinuxVersion = false
        var webLaunchLabel: String? = null

        if (ItchWebsiteUtils.isStorePage(doc)) {
            val db = AppDatabase.getDatabase(context)
            ItchWebsiteParser.getGameInfoForStorePage(doc, url)?.let { gameInfo ->
                val (webEntryPoint, webLabel) = ItchWebsiteParser.getWebGameUrlAndLabel(context, doc)
                webLaunchLabel = webLabel
                game = gameInfo.copy(
                    webEntryPoint = webEntryPoint
                )
                Log.d(LOGGING_TAG, "Adding game $game")
                db.gameDao.upsert(game!!)

                for (bundle in SpecialBundle.values()) {
                    if (bundle.containsGame(gameInfo.gameId)) {
                        Log.d(LOGGING_TAG, "Belongs to special bundle: " + bundle.slug)

                        val userName = ItchWebsiteUtils.getLoggedInUserName(doc)
                        bundleLink = SpecialBundleHandler.getLinkForUser(context, bundle, userName)

                        if (bundleLink != null) {
                            specialBundle = bundle
                            Log.d(LOGGING_TAG, "Got link for bundle!")
                            break
                        }
                    }
                }
            }
            purchasedInfo = ItchWebsiteParser.getPurchasedInfo(doc)
            paymentInfo = ItchWebsiteParser.getPaymentInfo(doc)

            hasAndroidVersion = ItchWebsiteParser.hasAndroidInstallation(doc)
            hasWindowsMacLinuxVersion = ItchWebsiteParser.hasWindowsMacLinuxInstallation(doc)
        }
        if (ItchWebsiteUtils.hasGameDownloadLinks(doc)) {
            lastDownloadDoc = doc
            lastDownloadPageUrl = url
            tryStartDownload()
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(true) {
            if (ItchWebsiteUtils.shouldHandleDayNightThemes(doc)) {
                if (ItchWebsiteUtils.isDarkTheme(doc))
                    putString("current_site_theme", "dark")
                else
                    putString("current_site_theme", "light")
            }
            val locale = ItchWebsiteParser.getLocale(doc)
            if (locale != ItchWebsiteParser.UNKNOWN_LOCALE) {
                putString(PREF_LANG_SITE_LOCALE, locale)
            }
        }
        if (SpecialBundleHandler.checkIsBundleLink(context, doc, url)) {
            Log.d(LOGGING_TAG, "Is bundle link! $url")
        }
        return Info(
            game = game,
            specialBundle = specialBundle,
            bundleDownloadLink = bundleLink,
            purchasedInfo = purchasedInfo,
            paymentInfo = paymentInfo,
            hasAndroidVersion = hasAndroidVersion,
            hasWindowsMacOrLinuxVersion = hasWindowsMacLinuxVersion,
            webLaunchLabel = webLaunchLabel
        )
    }

    fun onStartedOfflineWebGame(game: Game): Info {
        return Info(
            game = game,
            specialBundle = null,
            bundleDownloadLink = null,
            purchasedInfo = emptyList(),
            paymentInfo = null,
            hasAndroidVersion = false,
            hasWindowsMacOrLinuxVersion = false,
            isRunningCachedWebGame = true,
            isCachedWebGameOffline = true
        )
    }

    suspend fun setClickedUploadId(uploadId: Int) {
        Log.d(LOGGING_TAG, "Set upload ID: $uploadId")
        clickedUploadId = uploadId
        tryStartDownload()
    }

    suspend fun onDownloadStarted(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long?
    ) {
        currentDownloadUrl = url
        currentDownloadContentDisposition = contentDisposition
        currentDownloadMimeType = mimeType
        currentDownloadContentLength = contentLength
        tryStartDownload()
    }

    private suspend fun tryStartDownload() {
        Log.d(LOGGING_TAG, "Upload ID: $clickedUploadId")

        val downloadPageDoc = lastDownloadDoc ?: return
        val downloadPageUrl = lastDownloadPageUrl ?: return
        val uploadId = clickedUploadId ?: return
        val downloadUrl = currentDownloadUrl ?: return
        val contentDisposition = currentDownloadContentDisposition ?: return
        val mimeType = currentDownloadMimeType ?: return
        val contentLength = currentDownloadContentLength ?: return

        clickedUploadId = null
        currentDownloadUrl = null
        currentDownloadContentDisposition = null
        currentDownloadMimeType = null
        currentDownloadContentLength = null

        coroutineScope {
            launch(Dispatchers.Main) {
                Toast.makeText(context, R.string.popup_download_started, Toast.LENGTH_LONG)
                    .show()
            }

            launch {
                val pendingInstall =
                    ItchWebsiteParser.getPendingInstallation(downloadPageDoc, uploadId)

                Log.d(LOGGING_TAG, "content length: $contentLength")
                GameDownloader.requestDownload(context, pendingInstall, downloadUrl,
                    downloadPageUrl, contentDisposition, mimeType, contentLength)
            }
        }
    }

    suspend fun getGameEmbedInfoFromId(gameId: Int): Game {
        val doc = ItchWebsiteUtils.fetchAndParse("https://itch.io/embed/$gameId")

        return Game(
            gameId = gameId,
            name = doc.selectFirst("h1")!!.text(),
            author = doc.selectFirst(".author_row a")!!.text(),
            storeUrl = doc.selectFirst(".button_row a")!!.absUrl("href"),
            thumbnailUrl = doc.selectFirst(".thumb")!!.absUrl("src")
        )
    }
}