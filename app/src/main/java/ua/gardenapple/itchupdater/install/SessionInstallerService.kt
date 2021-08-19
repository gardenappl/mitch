package ua.gardenapple.itchupdater.install

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import java.io.File

/**
 * This service gets started after an APK gets installed by an Installer.
 * AFAIK this has to be a service because it calls [Service.startActivity],
 * but I'm not sure because I copied this from Aurora Store.
 */
class SessionInstallerService : Service() {
    companion object {
        private const val LOGGING_TAG = "InstallerService"

        const val EXTRA_APK_PATH = "APK_PATH"
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(LOGGING_TAG, Utils.toString(intent.extras))
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)!!

        //InstallerService shouldn't receive intent for Mitch anyway,
        //this is handled by SelfUpdateBroadcastReceiver
        if (status == PackageInstaller.STATUS_SUCCESS && packageName == applicationContext.packageName) {
            stopSelf()
            return START_STICKY
        }

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmationIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(confirmationIntent)
            }
            else -> runBlocking(Dispatchers.IO) {
                Installations.onInstallResult(applicationContext, sessionId.toLong(),
                    packageName, File(apkPath), status)
            }
        }

        stopSelf()

        return START_NOT_STICKY
    }
}