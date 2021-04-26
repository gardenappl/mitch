package ua.gardenapple.itchupdater

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.*
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2okhttp.OkHttpDownloader
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.MailSenderConfigurationBuilder
import org.acra.data.StringFormat
import ua.gardenapple.itchupdater.client.UpdateChecker
import ua.gardenapple.itchupdater.database.DatabaseCleanup
import ua.gardenapple.itchupdater.files.DownloadFileManager
import ua.gardenapple.itchupdater.files.ExternalFileManager
import ua.gardenapple.itchupdater.installer.FileDownloadListener
import ua.gardenapple.itchupdater.installer.Installer
import ua.gardenapple.itchupdater.installer.InstallerDatabaseHandler
import ua.gardenapple.itchupdater.ui.CrashDialog
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
const val NOTIFICATION_TAG_INSTALL_RESULT = "InstallResult"

const val UPDATE_CHECK_TASK_TAG = "update_check"
const val DB_CLEAN_TASK_TAG = "db_clean"

const val FLAVOR_FDROID = "fdroid"
const val FLAVOR_ITCHIO = "itchio"

//const val PREF_LAST_UPDATE_CHECK = "ua.gardenapple.itchupdater.lastupdatecheck"
const val PREF_DB_RAN_CLEANUP_ONCE = "ua.gardenapple.itchupdater.db_cleanup_once"


class Mitch : Application() {

    companion object {
        const val LOGGING_TAG: String = "MitchApp"

        lateinit var httpClient: OkHttpClient
            private set
        private lateinit var fetch: Fetch
        lateinit var fileManager: DownloadFileManager
            private set
        lateinit var databaseHandler: InstallerDatabaseHandler
            private set
        lateinit var externalFileManager: ExternalFileManager
            private set

        val installer: Installer by lazy {
            Installer()
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
            }
        }

    override fun onCreate() {
        super.onCreate()

        if (ACRA.isACRASenderServiceProcess())
            return

        setThemeFromPreferences(PreferenceManager.getDefaultSharedPreferences(this))

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


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val workOnMetered = sharedPreferences.getBoolean("preference_update_check_if_metered", true)
        registerUpdateCheckTask(!workOnMetered, ExistingPeriodicWorkPolicy.KEEP)

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        val okHttpCacheDir = File(cacheDir, "OkHttp")
        okHttpCacheDir.mkdirs()
        httpClient = OkHttpClient.Builder().run {
            cache(Cache(
                directory = okHttpCacheDir,
                maxSize = 10 * 1024 * 1024 //10 MB
            ))
            build()
        }
        val fetchConfig = FetchConfiguration.Builder(applicationContext).run {
            setDownloadConcurrentLimit(3)
            setHttpDownloader(OkHttpDownloader(httpClient))
            setAutoRetryMaxAttempts(3)
            enableFileExistChecks(false)
            build()
        }
        fetch = fetchConfig.getNewFetchInstanceFromConfiguration()
        fetch.addListener(FileDownloadListener(applicationContext))
        fileManager = DownloadFileManager(applicationContext, fetch)
        fileManager.setup()

        databaseHandler = InstallerDatabaseHandler(applicationContext)
        externalFileManager = ExternalFileManager()


        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            DB_CLEAN_TASK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DatabaseCleanup.Worker>(2, TimeUnit.DAYS).build()
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
                //setInitialDelay(1, TimeUnit.MINUTES)
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

    /**
     * ACRA crash reports
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        ACRA.init(this, CoreConfigurationBuilder(this).apply {
            setBuildConfigClass(BuildConfig::class.java)
            setReportFormat(StringFormat.KEY_VALUE_LIST)

            getPluginConfigurationBuilder(MailSenderConfigurationBuilder::class.java).apply {
                setMailTo("gardenapple+mitch@posteo.net")
                setSubject("Mitch bug report")
                setResBody(R.string.bug_report_dialog_prompt)
                setEnabled(true)
                setReportFileName("crash-report.txt")
            }

            getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java).apply {
                setReportDialogClass(CrashDialog::class.java)
                setEnabled(true)
            }
        })
    }
}