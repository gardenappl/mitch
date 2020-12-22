package ua.gardenapple.itchupdater.installer

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase

/**
 * This service gets started after an APK gets installed by an Installer.
 */
class InstallerService : Service() {
    companion object {
        private const val LOGGING_TAG = "InstallerService"

        const val EXTRA_APK_NAME = "APK_NAME"
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(LOGGING_TAG, "onStartCommand")
        Log.d(LOGGING_TAG, intent.dataString ?: "null")
        Log.d(LOGGING_TAG, Utils.toString(intent.extras))
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val apkName = intent.getStringExtra(EXTRA_APK_NAME)

        when(status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmationIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(confirmationIntent)

                stopSelf()
            }
            else -> {
                runBlocking(Dispatchers.IO) {
                    InstallerEvents.notifyApkInstallComplete(sessionId, packageName!!, apkName, status)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }
}