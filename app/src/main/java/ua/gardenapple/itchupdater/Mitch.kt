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
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.MailSenderConfigurationBuilder
import org.acra.data.StringFormat
import ua.gardenapple.itchupdater.client.UpdateChecker
import ua.gardenapple.itchupdater.database.DatabaseCleanup
import ua.gardenapple.itchupdater.download.FetchDownloader
import ua.gardenapple.itchupdater.download.WorkerDownloader
import ua.gardenapple.itchupdater.download.MitchFetchListener
import ua.gardenapple.itchupdater.files.*
import ua.gardenapple.itchupdater.install.SessionInstaller
import ua.gardenapple.itchupdater.install.InstallerDatabaseHandler
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
const val NOTIFICATION_TAG_DOWNLOAD_LONG = "WorkerDownloadResult"
const val NOTIFICATION_TAG_INSTALL_RESULT = "InstallResult"
const val NOTIFICATION_TAG_INSTALL_RESULT_LONG = "NativeInstallResult"

const val UPDATE_CHECK_TASK_TAG = "update_check"
const val DB_CLEAN_TASK_TAG = "db_clean"

const val FLAVOR_FDROID = "fdroid"
const val FLAVOR_ITCHIO = "itchio"

//const val PREF_LAST_UPDATE_CHECK = "ua.gardenapple.itchupdater.lastupdatecheck"
const val PREF_DB_RAN_CLEANUP_ONCE = "ua.gardenapple.itchupdater.db_cleanup_once"
const val PREF_DOWNLOADER = "ua.gardenapple.itchupdater.downloader"
const val PREF_INSTALLER = "ua.gardenapple.itchupdater.installer"
const val PREF_JUSTICE_LINK = "mitch.justice"
const val PREF_PREFIX_JUSTICE_LINK = "mitch.justice_"
const val PREF_PREFIX_JUSTICE_LAST_CHECK = "mitch.justicetimestamp_"


class Mitch : Application() {

    companion object {
        const val LOGGING_TAG: String = "MitchApp"

        lateinit var httpClient: OkHttpClient
            private set
        private lateinit var fetch: Fetch
        lateinit var fileManager: DownloadFileManager
            private set
        lateinit var workerDownloader: WorkerDownloader
            private set
        lateinit var databaseHandler: InstallerDatabaseHandler
            private set
        lateinit var externalFileManager: ExternalFileManager
            private set
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
            //TODO: Maybe OkHttpDownloader is causing issue #22?
//            setHttpDownloader(OkHttpDownloader(httpClient))
            setAutoRetryMaxAttempts(3)
            enableFileExistChecks(false)
            createDownloadFileOnEnqueue(false)
            preAllocateFileOnCreation(false)
            build()
        }
        fetch = fetchConfig.getNewFetchInstanceFromConfiguration()
        val fetchDownloader = FetchDownloader(fetch)
        fetch.addListener(MitchFetchListener(applicationContext, fetchDownloader))
        fileManager = DownloadFileManager(applicationContext, fetchDownloader)
        fileManager.setup()

        databaseHandler = InstallerDatabaseHandler(applicationContext)
        externalFileManager = ExternalFileManager()
        workerDownloader = WorkerDownloader()


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

    /**
     * ACRA crash reports
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        ACRA.init(this, CoreConfigurationBuilder(this).apply {
            setBuildConfigClass(BuildConfig::class.java)
            setReportFormat(StringFormat.KEY_VALUE_LIST)

            getPluginConfigurationBuilder(MailSenderConfigurationBuilder::class.java).apply {
                setMailTo("~gardenapple/mitch-bug-reports@lists.sr.ht, gardenapple+mitch@posteo.net")
                //Empty subject, user should write their own
                setSubject("")
                //Strings are English only, this is intentional
                setBody("""
                    > Your message will be published on SourceHut,
                    > and also sent to the author's personal address.
                    
                    > Note: SourceHut does not accept email in HTML format,
                    > for security and privacy reasons.
                    > Please send this message as "plain text" if you can.
                    
                    > Thank you for your help!
                    
                    > Insert description of what you were doing when you got the error:
                """.trimIndent())
                setReportFileName("error-report-and-logs.txt")
                setEnabled(true)
            }

            getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java).apply {
                setReportDialogClass(CrashDialog::class.java)
                setEnabled(true)
            }
        })
    }
}