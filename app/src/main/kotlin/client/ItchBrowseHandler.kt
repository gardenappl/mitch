package garden.appl.mitch.client

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.PREF_LANG_SITE_LOCALE
import garden.appl.mitch.PREF_WARN_WRONG_OS
import garden.appl.mitch.R
import garden.appl.mitch.data.SpecialBundle
import garden.appl.mitch.data.containsGame
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.ui.MitchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document

class ItchBrowseHandler(private val context: MitchActivity, private val scope: CoroutineScope) {
    companion object {
        private const val LOGGING_TAG = "ItchBrowseHandler"

        // ItchBrowseHandler may be re-created on fragment re-attachment,
        // but I want these values to be retained. Making them static is a lazy solution.
        // Looking back, the @Volatile annotations are painful,
        // but I'd rather not get into this now either.
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
        @Volatile
        private var currentUserAgent: String? = null
    }

    data class Info(
        val specialBundle: SpecialBundle?,
        val bundleDownloadLink: String?,
        val game: Game?,
        val purchasedInfo: List<ItchWebsiteParser.PurchasedInfo>,
        val paymentInfo: ItchWebsiteParser.PaymentInfo?,
        val hasAndroidVersion: Boolean,
        val hasWindowsMacOrLinuxVersion: Boolean,
        val webLaunchLabel: String? = null
    )

    suspend fun onPageVisited(doc: Document, url: String, userAgent: String): Info {
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
                webLaunchLabel = ItchWebsiteParser.getWebGameLabel(context, doc)
                game = gameInfo
                Log.d(LOGGING_TAG, "Adding game $game")
                db.gameDao.upsert(game)

                for (bundle in SpecialBundle.entries) {
                    if (bundle.containsGame(gameInfo.gameId)) {
                        Log.d(LOGGING_TAG, "Belongs to special bundle: " + bundle.slug)

                        val userName = ItchWebsiteUtils.getLoggedInUserName(doc)
                        bundleLink = SpecialBundleHandler.getLinkForUser(
                            context,
                            bundle,
                            userName,
                            userAgent
                        )

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
        prefs.edit(commit = true) {
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

    suspend fun setClickedUploadId(uploadId: Int) {
        Log.d(LOGGING_TAG, "Set upload ID: $uploadId")
        clickedUploadId = uploadId
        tryStartDownload()
    }

    suspend fun onDownloadStarted(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long?
    ) {
        currentDownloadUrl = url
        currentUserAgent = userAgent
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
        val userAgent = currentUserAgent ?: return
        val contentDisposition = currentDownloadContentDisposition ?: return
        val mimeType = currentDownloadMimeType ?: return
        val contentLength = currentDownloadContentLength ?: return

        val install = ItchWebsiteParser.getPendingInstallation(downloadPageDoc, uploadId)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (prefs.getBoolean(PREF_WARN_WRONG_OS, true) && install.platforms != 0
            && install.platforms and Installation.PLATFORM_ANDROID == 0) {

            scope.launch(Dispatchers.Main) {
                val dialog = AlertDialog.Builder(context).run {
                    setTitle(android.R.string.dialog_alert_title)
                    setIconAttribute(android.R.attr.alertDialogIcon)
                    setMessage(context.getString(R.string.dialog_download_wrong_os,
                        install.uploadName))
                    setPositiveButton(R.string.dialog_yes) { _, _ ->
                        scope.launch {
                            doDownload(install, downloadPageUrl, downloadUrl,
                                userAgent, contentDisposition, mimeType, contentLength)
                        }
                    }
                    setNegativeButton(R.string.dialog_no) { _, _ ->
                        //no-op
                    }

                    create()
                }
                dialog.show()
            }
        } else {
            doDownload(install, downloadPageUrl, downloadUrl,
                userAgent, contentDisposition, mimeType, contentLength)
        }
    }

    private suspend fun doDownload(
        pendingInstall: Installation,
        downloadPageUrl: String,
        downloadUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        clickedUploadId = null
        currentDownloadUrl = null
        currentUserAgent = null
        currentDownloadContentDisposition = null
        currentDownloadMimeType = null
        currentDownloadContentLength = null


        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, R.string.popup_download_started, Toast.LENGTH_LONG)
                .show()
        }

        context.requestNotificationPermission(
            scope,
            R.string.dialog_notification_explain_download,
            R.string.dialog_notification_cancel_download
        )
        GameDownloader.requestDownload(context, pendingInstall, downloadUrl, userAgent,
            downloadPageUrl, contentDisposition, mimeType, contentLength)
    }
}