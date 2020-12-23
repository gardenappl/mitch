package ua.gardenapple.itchupdater

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import ua.gardenapple.itchupdater.client.UpdateCheckWorker
import ua.gardenapple.itchupdater.gitlab.GitlabUpdateCheckWorker
import ua.gardenapple.itchupdater.installer.*
import java.io.File
import java.util.concurrent.TimeUnit


const val LOGGING_TAG: String = "Mitch"

const val PERMISSION_REQUEST_CODE_DOWNLOAD = 1

const val NOTIFICATION_CHANNEL_ID_UPDATES = "updates_available"
const val NOTIFICATION_CHANNEL_ID_INSTALL = "updates"
const val NOTIFICATION_CHANNEL_ID_INSTALLING = "installing"

const val NOTIFICATION_ID_SELF_UPDATE_CHECK = 999_999_999
const val NOTIFICATION_ID_UPDATE_CHECK = 1_000_000_000
const val NOTIFICATION_ID_DOWNLOAD = 20000
const val NOTIFICATION_ID_INSTALLING = 1000000

const val UPDATE_CHECK_TASK_TAG = "update_check"
const val GITLAB_UPDATE_CHECK_TASK_TAG = "gitlab_check"

const val FLAVOR_FDROID = "fdroid"
const val FLAVOR_ITCHIO = "itchio"
const val FLAVOR_GITLAB = "gitlab"

//TODO: Catch exceptions in a nice way
class MitchApp : Application() {

    companion object {
        lateinit var httpClient: OkHttpClient
            private set

        val installer: Installer by lazy {
            Installer()
        }
    }

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "preference_update_check_if_metered" -> {
                    //WorkManager.getInstance(applicationContext).cancelAllWorkByTag(UPDATE_CHECK_TASK_TAG)
                    registerUpdateCheckTask(requiresUnmetered = prefs.getBoolean(key, false))
                    Log.d(LOGGING_TAG, "Re-registering...")
                }
                "preference_theme" -> setThemeFromPreferences(prefs)
                "current_site_theme" -> {
                    if (prefs.getString("preference_theme", "site") == "site") {
                        when (prefs.getString(key, "light")) {
                            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        }
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        setThemeFromPreferences(PreferenceManager.getDefaultSharedPreferences(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var name = getString(R.string.notification_channel_install)
            var descriptionText = getString(R.string.notification_channel_install_desc)
            var importance = NotificationManager.IMPORTANCE_HIGH
            var channel =
                NotificationChannel(NOTIFICATION_CHANNEL_ID_INSTALL, name, importance).apply {
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
        }


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val workOnMetered = sharedPreferences.getBoolean("preference_update_check_if_metered", true)
        registerUpdateCheckTask(!workOnMetered)

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)


        InstallerEvents.setup()
        val installerDatabaseHandler = InstallerDatabaseHandler(applicationContext)
        InstallerEvents.addListener(installerDatabaseHandler as DownloadCompleteListener)
        InstallerEvents.addListener(installerDatabaseHandler as InstallCompleteListener)
        val notificationHandler = InstallerNotificationHandler(applicationContext)
        InstallerEvents.addListener(notificationHandler)


        val okHttpCacheDir = File(cacheDir, "OkHttp")
        okHttpCacheDir.mkdirs()
        httpClient = OkHttpClient.Builder().run {
            cache(Cache(
                directory = okHttpCacheDir,
                maxSize = 10 * 1024 * 1024 //10 MB
            ))
            build()
        }
    }

    private fun registerUpdateCheckTask(requiresUnmetered: Boolean) {
        val constraints = Constraints.Builder().run {
            if(requiresUnmetered)
                setRequiredNetworkType(NetworkType.UNMETERED)
            else
                setRequiredNetworkType(NetworkType.CONNECTED)
            build()
        }
        val updateCheckRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).run {
            //addTag(UPDATE_CHECK_TASK_TAG)
            setConstraints(constraints)
            //setInitialDelay(1, TimeUnit.MINUTES)
            build()
        }

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                UPDATE_CHECK_TASK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                updateCheckRequest
            )

        if (BuildConfig.FLAVOR == FLAVOR_GITLAB) {
            val gitlabUpdateCheckRequest =
                PeriodicWorkRequestBuilder<GitlabUpdateCheckWorker>(1, TimeUnit.DAYS).run {
                    setConstraints(constraints)
                    build()
                }

            WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    GITLAB_UPDATE_CHECK_TASK_TAG,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    gitlabUpdateCheckRequest
                )
        }
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
}