package ua.gardenapple.itchupdater

import android.app.Application
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import okhttp3.Cache
import okhttp3.OkHttpClient
import ua.gardenapple.itchupdater.installer.DownloadRequester
import java.io.File


const val LOGGING_TAG: String = "Mitch"

const val PERMISSION_REQUEST_CODE_DOWNLOAD = 1

const val NOTIFICATION_CHANNEL_ID_UPDATES = "updates"

const val NOTIFICATION_ID_DOWNLOAD = 20000

const val FLAVOR_FDROID = "fdroid"
const val FLAVOR_ITCHIO = "itchio"

class MitchApp : Application(),
        ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        lateinit var httpClient: OkHttpClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_install)
            val descriptionText = getString(R.string.notification_channel_install_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID_UPDATES, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val okHttpCacheDir = File(cacheDir, "OkHttp")
        okHttpCacheDir.mkdirs()
        httpClient = OkHttpClient.Builder().run {
            cache(Cache(
                directory = okHttpCacheDir,
                maxSize = 50 * 1024 * 1024 //50 MB
            ))
            build()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE_DOWNLOAD -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    DownloadRequester.resumeDownload(getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                }
            }
        }
    }
}