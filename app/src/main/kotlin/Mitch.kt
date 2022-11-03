package garden.appl.mitch

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.acra.ACRA
import org.acra.ReportField
import org.acra.data.StringFormat
import garden.appl.mitch.client.UpdateChecker
import garden.appl.mitch.database.DatabaseCleanup
import garden.appl.mitch.files.DownloadFileManager
import garden.appl.mitch.files.ExternalFileManager
import garden.appl.mitch.files.WebGameCache
import garden.appl.mitch.install.InstallerDatabaseHandler
import garden.appl.mitch.ui.CrashDialog
import garden.appl.mitch.ui.MitchContextWrapper
import org.acra.config.*
import org.acra.ktx.initAcra
import java.io.File
import java.util.concurrent.TimeUnit


const val PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT = 1
const val PERMISSION_REQUEST_MOVE_TO_DOWNLOADS = 2

const val FILE_PROVIDER = "ua.gardenapple.itchupdater.fileprovider"

const val NOTIFICATION_CHANNEL_ID_UPDATES = "updates_available"
const val NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED = "updates"
const val NOTIFICATION_CHANNEL_ID_INSTALLING = "installing"
const val NOTIFICATION_CHANNEL_ID_WEB_RUNNING = "web_running"

const val NOTIFICATION_TAG_UPDATE_CHECK = "UpdateCheck"
const val NOTIFICATION_TAG_DOWNLOAD = "DownloadResult"
const val NOTIFICATION_TAG_DOWNLOAD_LONG = "DownloadResultLong"
const val NOTIFICATION_TAG_INSTALL_RESULT = "InstallResult"
const val NOTIFICATION_TAG_INSTALL_RESULT_LONG = "NativeInstallResult"

const val UPDATE_CHECK_TASK_TAG = "update_check"
const val DB_CLEAN_TASK_TAG = "db_clean"

const val FLAVOR_FDROID = "fdroid"
const val FLAVOR_ITCHIO = "itchio"


// Remember to exclude sensitive info from ACRA reports
const val PREF_DB_RAN_CLEANUP_ONCE = "ua.gardenapple.itchupdater.db_cleanup_once"
const val PREF_INSTALLER = "ua.gardenapple.itchupdater.installer"
const val PREF_WEB_ANDROID_FILTER = "ua.gardenapple.itchupdater.web_android_filter"
// Bundles: mitch.{racial, palestine, ukraine, trans_texas}
const val PREF_LANG = "mitch.lang"
/**
 * Locale is not controlled directly by the user; instead, Mitch.kt applies
 * [PREF_LANG_LOCALE_NEXT], and then [PREF_LANG_LOCALE] gets applied on app restart
 */
const val PREF_LANG_LOCALE = "mitch.lang_locale"
const val PREF_LANG_LOCALE_NEXT = "mitch.lang_locale_next"
const val PREF_LANG_SITE_LOCALE = "mitch.lang_site_locale"
const val PREF_WARN_WRONG_OS = "mitch.warn_wrong_os"
const val PREF_WEB_CACHE_ENABLE = "mitch.web_cache_enable"
const val PREF_WEB_CACHE_UPDATE = "mitch.web_cache_update"
const val PREF_WEB_CACHE_DIALOG_HIDE = "mitch.web_cache_dialog_hide"
object PreferenceWebCacheUpdate {
    const val NEVER = "never"
    const val UNMETERED = "unmetered"
    const val ALWAYS ="always"
}
const val PREF_BROWSE_START_PAGE = "mitch.browse_start_page"
const val PREF_START_PAGE_EXCLUDE = "mitch.start_page_exclude"
const val PREF_START_PAGE_EXCLUDE_DISPLAY_STRING = "mitch.start_page_exclude.display_string"

const val PREF_DEBUG_DISABLE_GAME_ACTIVITY = "mitch.debug.disable_game_activity"



class Mitch : Application() {

    companion object {
        const val LOGGING_TAG: String = "MitchApp"

        // Used for lazy initialization, and for locale stuff
        private lateinit var mitchContext: MitchContextWrapper
        private lateinit var cacheDir: File

        // Be careful with lazy init to avoid circular dependency, I'm stupid

        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder().let {
                val okHttpCacheDir = File(Mitch.cacheDir, "OkHttp")
                okHttpCacheDir.mkdirs()
                it.cache(Cache(
                    directory = okHttpCacheDir,
                    maxSize = 10L * 1024 * 1024 //10 MB
                ))
                it.build()
            }
        }
        val fileManager: DownloadFileManager by lazy {
            DownloadFileManager(mitchContext).apply {
                setup()
            }
        }
        val externalFileManager = ExternalFileManager()
        val databaseHandler: InstallerDatabaseHandler by lazy {
            InstallerDatabaseHandler(mitchContext)
        }
        val webGameCache: WebGameCache by lazy {
            WebGameCache(mitchContext)
        }
    }

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "preference_update_check_if_metered" -> {
                    registerUpdateCheckTask(prefs.getBoolean(key, false),
                        ExistingPeriodicWorkPolicy.REPLACE)
                }
                "preference_theme",
                "current_site_theme" -> setThemeFromPreferences(prefs)
                PREF_LANG,
                PREF_LANG_SITE_LOCALE -> setLangFromPreferences(prefs)
            }
        }


    override fun onCreate() {
        super.onCreate()
        if (ACRA.isACRASenderServiceProcess())
            return


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        sharedPreferences.edit(true) {
            val nextLocale = sharedPreferences.getString(PREF_LANG_LOCALE_NEXT, null)
            if (nextLocale != null) {
                remove(PREF_LANG_LOCALE_NEXT)
                putString(PREF_LANG_LOCALE, nextLocale)
            }
        }
        setLangFromPreferences(sharedPreferences)
        setThemeFromPreferences(sharedPreferences)

        mitchContext = MitchContextWrapper.wrap(applicationContext,
            sharedPreferences.getString(PREF_LANG_LOCALE, "")!!)
        Mitch.cacheDir = cacheDir



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var name = getString(R.string.notification_channel_install)
            var descriptionText = getString(R.string.notification_channel_install_desc)
            var importance = NotificationManager.IMPORTANCE_HIGH
            var channel =
                NotificationChannel(NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED, name, importance).apply {
                    description = descriptionText
                }
            // Register the channel with the system
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)


            name = getString(R.string.notification_channel_updates)
            descriptionText = getString(R.string.notification_channel_updates_desc)
            importance = NotificationManager.IMPORTANCE_DEFAULT
            channel = NotificationChannel(NOTIFICATION_CHANNEL_ID_UPDATES, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)


            name = getString(R.string.notification_channel_installing)
            descriptionText = getString(R.string.notification_channel_installing_desc)
            importance = NotificationManager.IMPORTANCE_DEFAULT
            channel =
                NotificationChannel(NOTIFICATION_CHANNEL_ID_INSTALLING, name, importance).apply {
                    description = descriptionText
                }
            notificationManager.createNotificationChannel(channel)


            name = getString(R.string.notification_channel_web_running)
            descriptionText = getString(R.string.notification_channel_web_running_desc)
            importance = NotificationManager.IMPORTANCE_LOW
            channel = NotificationChannel(NOTIFICATION_CHANNEL_ID_WEB_RUNNING, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val workOnMetered = sharedPreferences.getBoolean("preference_update_check_if_metered", true)
        registerUpdateCheckTask(!workOnMetered, ExistingPeriodicWorkPolicy.KEEP)

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            DB_CLEAN_TASK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DatabaseCleanup.Worker>(1, TimeUnit.DAYS).build()
        )
    }

    private fun registerUpdateCheckTask(
        requiresUnmetered: Boolean,
        existingWorkPolicy: ExistingPeriodicWorkPolicy
    ) {
        val constraints = Constraints.Builder().run {
            if (requiresUnmetered)
                setRequiredNetworkType(NetworkType.UNMETERED)
            else
                setRequiredNetworkType(NetworkType.CONNECTED)
            build()
        }
        val updateCheckRequest =
            PeriodicWorkRequestBuilder<UpdateChecker.Worker>(1, TimeUnit.DAYS).run {
                //addTag(UPDATE_CHECK_TASK_TAG)
                setConstraints(constraints)
                setInitialDelay(10, TimeUnit.HOURS)
                build()
            }

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            UPDATE_CHECK_TASK_TAG,
            existingWorkPolicy,
            updateCheckRequest
        )
    }

    private fun setThemeFromPreferences(prefs: SharedPreferences) {
        when (prefs.getString("preference_theme", "site")) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "site" -> when (prefs.getString("current_site_theme", null)) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    private fun setLangFromPreferences(prefs: SharedPreferences) {
        val systemLocale = Utils.getPreferredLocale(applicationContext).toLanguageTag()
        val newLocale = when (prefs.getString(PREF_LANG, "default")) {
            "system" -> systemLocale
            "site" -> prefs.getString(PREF_LANG_SITE_LOCALE, "en")
            else -> {
                val siteLocale = prefs.getString(PREF_LANG_SITE_LOCALE, "en")
                if (siteLocale == "en")
                    systemLocale
                else
                    siteLocale
            }
        }
        prefs.edit(true) {
            if (newLocale != prefs.getString(PREF_LANG_LOCALE_NEXT,
                    prefs.getString(PREF_LANG_LOCALE, systemLocale)))
                putString(PREF_LANG_LOCALE_NEXT, newLocale)
        }
    }

    /**
     * ACRA crash reports
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.KEY_VALUE_LIST
            reportContent = listOf(
                ReportField.ANDROID_VERSION,
                ReportField.BUILD_CONFIG,
                ReportField.STACK_TRACE,
                ReportField.LOGCAT,
                ReportField.SHARED_PREFERENCES
            )
            excludeMatchingSharedPreferencesKeys = listOf(".*(racial|justice|palestine|ukraine|trans_texas).*")

            mailSender {
                mailTo = "~gardenapple/mitch-bug-reports@lists.sr.ht, mitch@appl.garden"
                //Empty subject, user should write their own
                subject = ""
                //Email body is English only, this is intentional
                body = """
                    > Please describe what you were doing when you got the error.
                    
                    > Note: SourceHut does not accept email in HTML format,
                    > for security and privacy reasons.
                    > Please send this message as "plain text" if you can.
                    
                    > Your message will be published on SourceHut,
                    > and also sent to the developer's personal address.
                    
                    > Thank you for your help!
                """.trimIndent()
                reportFileName = "error-report-and-logs.txt"
            }

            dialog {
                reportDialogClass = CrashDialog::class.java
            }
        }
    }
}
