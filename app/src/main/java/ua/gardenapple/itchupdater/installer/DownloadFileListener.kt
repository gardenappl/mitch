package ua.gardenapple.itchupdater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import com.tonyodev.fetch2.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.ui.MainActivity
import java.io.File
import java.util.*

abstract class DownloadFileListener {
    companion object {
        private const val LOGGING_TAG = "FileDownloadListener"
    }

    private fun createResultNotification(context: Context, downloadFile: File, downloadId: Int,
                                         isApk: Boolean, errorName: String?) {
        val pendingIntent: PendingIntent?
        if (errorName != null) {
            pendingIntent = null
        } else if (isApk) {
            Log.d(LOGGING_TAG, downloadFile.path)

            val intent = Intent(context, InstallNotificationBroadcastReceiver::class.java).apply {
                data = Uri.fromFile(downloadFile)
                putExtra(InstallNotificationBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            }
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            val fileIntent = Utils.getIntentForFile(context, downloadFile, FILE_PROVIDER)

            val intent = if (fileIntent.resolveActivity(context.packageManager) == null) {
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOULD_OPEN_LIBRARY, true)
            } else {
                Intent.createChooser(fileIntent, context.resources.getString(R.string.select_app_for_file))
            }
            pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            if (errorName != null) {
                setContentTitle(downloadFile.name)
                setContentText(context.resources.getString(R.string.notification_download_error, errorName))
            } else {
                if (isApk)
                    setContentTitle(context.resources.getString(R.string.notification_install_title))
                else
                    setContentTitle(context.resources.getString(R.string.notification_download_complete_title))
                setContentText(downloadFile.name)
            }

            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_TAG_DOWNLOAD, downloadId, builder.build())
        }
    }

    private fun createProgressNotification(context: Context, downloadFile: File,
                                           downloadId: Int, uploadId: Int,
                                           progressPercent: Int?, etaInMilliSeconds: Long?) {
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
            setOngoing(true)
            setOnlyAlertOnce(true)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.ic_mitch_notification)
            setContentTitle(downloadFile.name)

            if (progressPercent != null)
                setProgress(100, progressPercent, false)
            else
                setProgress(100, 0, true)

            if (etaInMilliSeconds != null)
                setContentInfo(context.resources.getString(R.string.notification_download_time_remaining,
                        etaInMilliSeconds / 60_000, etaInMilliSeconds / 1000))

            val cancelIntent = Intent(context, DownloadCancelBroadcastReceiver::class.java).apply {
                putExtra(DownloadCancelBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(DownloadCancelBroadcastReceiver.EXTRA_UPLOAD_ID, uploadId)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            addAction(R.drawable.ic_baseline_cancel_24, context.getString(R.string.dialog_cancel),
                    cancelPendingIntent)
        }
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_TAG_DOWNLOAD, downloadId, builder.build())
        }
    }

    protected fun onCompleted(context: Context, file: String, uploadId: Int, downloadId: Int) {
        val isApk = file.endsWith(".apk")
        val downloadFileManager = Mitch.fileManager

        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.withTransaction {
                val pendingInstall = db.installDao.getPendingInstallationByDownloadId(downloadId)!!
                val notificationFile: File
                if (isApk) {
                    notificationFile = downloadFileManager.getPendingFile(uploadId)!!
                } else {
                    Installations.deleteOutdatedInstalls(context, pendingInstall)
                    downloadFileManager.replacePendingFile(uploadId)
                    notificationFile = downloadFileManager.getDownloadedFile(uploadId)!!
                }
                createResultNotification(context, notificationFile, downloadId, isApk, null)
                Mitch.databaseHandler.onDownloadComplete(pendingInstall, isApk)
            }
        }
    }

    protected fun onError(context: Context, file: File, downloadId: Int, uploadId: Int, errorName: String) {
        val isApk = file.extension == "apk"
        createResultNotification(context, file, downloadId, isApk, errorName)

        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.withTransaction {
                Mitch.fileManager.deletePendingFile(uploadId)
                Mitch.databaseHandler.onDownloadFailed(downloadId)
            }
        }
    }

    protected fun onProgress(context: Context, file: File, downloadId: Int, uploadId: Int,
                   progressPercent: Int?, etaInMilliSeconds: Long?) {
        createProgressNotification(context, file, downloadId, uploadId,
            progressPercent, etaInMilliSeconds)
    }
}
