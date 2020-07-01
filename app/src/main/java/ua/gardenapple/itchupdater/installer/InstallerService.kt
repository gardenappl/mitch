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

class InstallerService : Service() {
    companion object {
        const val LOGGING_TAG = "InstallerService"
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(LOGGING_TAG, "onStartCommand")
        Log.d(LOGGING_TAG, intent.dataString ?: "null")
        Log.d(LOGGING_TAG, Utils.toString(intent.extras))
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when(status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmationIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(confirmationIntent)

                stopSelf()
            }
            else -> {
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val installation = db.installDao.findPendingInstallationBySessionId(sessionId)!!
                    val game = db.gameDao.getGameById(installation.gameId)!!
                    InstallerEvents.notifyApkInstallComplete(sessionId, packageName!!, game, status)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }
}