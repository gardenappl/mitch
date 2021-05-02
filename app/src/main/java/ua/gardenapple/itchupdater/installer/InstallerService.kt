package ua.gardenapple.itchupdater.installer

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
        val apkName = intent.getStringExtra(EXTRA_APK_NAME)!!

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
            else -> {
                notifyInstallResult(sessionId, packageName!!, apkName, status)
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)

                    db.withTransaction {
                        val install = db.installDao.getPendingInstallationBySessionId(sessionId)!!
                        Mitch.fileManager.deletePendingFile(install.uploadId)
                        Installations.deleteOutdatedInstalls(applicationContext, install)
                        Mitch.databaseHandler.onInstallResult(install, packageName, status)

                        db.updateCheckDao.getUpdateCheckResultForUpload(install.uploadId)?.let {
                            it.isInstalling = false
                            db.updateCheckDao.insert(it)
                        }
                    }
                }
            }
        }

        stopSelf()

        return START_NOT_STICKY
    }

    /**
     * This method should *NOT* depend on the AppDatabase because this could be used for
     * the GitLab build update check, or other things
     */
    private fun notifyInstallResult(installSessionId: Int, packageName: String,
                            apkName: String, status: Int) {
        val context = applicationContext
        val message = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.resources.getString(R.string.notification_install_cancelled_title)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> context.resources.getString(R.string.notification_install_blocked_title)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> context.resources.getString(R.string.notification_install_conflict_title)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> context.resources.getString(R.string.notification_install_incompatible_title)
            PackageInstaller.STATUS_FAILURE_INVALID -> context.resources.getString(R.string.notification_install_invalid_title)
            PackageInstaller.STATUS_FAILURE_STORAGE -> context.resources.getString(R.string.notification_install_storage_title)
            PackageInstaller.STATUS_SUCCESS -> context.resources.getString(R.string.notification_install_complete_title)
            else -> context.resources.getString(R.string.notification_install_complete_title)
        }
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                setSmallIcon(R.drawable.ic_mitch_notification)

                if (status == PackageInstaller.STATUS_SUCCESS) {
                    try {
                        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                        setContentTitle(context.packageManager.getApplicationLabel(appInfo))
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(LOGGING_TAG, "Error: application $packageName not found!", e)
                        setContentTitle(apkName)
                    }
                } else {
                    setContentTitle(apkName)
                }

                setContentText(message)
//                priority = NotificationCompat.PRIORITY_HIGH
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    context.packageManager.getLaunchIntentForPackage(packageName)?.also { intent ->
                        val pendingIntent =
                            PendingIntent.getActivity(context, 0, intent, 0)
                        setContentIntent(pendingIntent)
                        setAutoCancel(true)
                    }

                    try {
                        val icon = context.packageManager.getApplicationIcon(packageName)
                        setLargeIcon(Utils.drawableToBitmap(icon))
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(LOGGING_TAG, "Could not load icon for $packageName", e)
                    }
                }
            }

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_TAG_INSTALL_RESULT, installSessionId, builder.build())
        }
    }
}