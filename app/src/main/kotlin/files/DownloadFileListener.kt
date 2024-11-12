package garden.appl.mitch.files

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.room.withTransaction
import garden.appl.mitch.ErrorReportBroadcastReceiver
import garden.appl.mitch.FILE_PROVIDER
import garden.appl.mitch.Mitch
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_INSTALLING
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD_LONG
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.install.InstallRequestBroadcastReceiver
import garden.appl.mitch.install.Installations
import garden.appl.mitch.ui.MainActivity
import garden.appl.mitch.ui.MitchActivity
import java.io.File

abstract class DownloadFileListener {
    companion object {
        private const val LOGGING_TAG = "FileDownloadListener"
    }

    private fun createResultNotification(
        context: Context,
        fileName: String,
        downloadType: DownloadType,
        downloadFile: File?,
        downloadOrInstallId: Long,
        errorName: String?,
        errorReport: String?
    ) {
        val pendingIntent: PendingIntent
        if (errorName != null) {
            val intent = Intent(context, ErrorReportBroadcastReceiver::class.java).apply {
                putExtra(ErrorReportBroadcastReceiver.EXTRA_ERROR_STRING, errorReport)
            }
            pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT, false)!!

        } else if (downloadType == DownloadType.SESSION_INSTALL) {
            val intent = Intent(context, InstallRequestBroadcastReceiver::class.java).apply {
                putExtra(InstallRequestBroadcastReceiver.EXTRA_STREAM_SESSION_ID, downloadOrInstallId)
                putExtra(InstallRequestBroadcastReceiver.EXTRA_APP_NAME, fileName)
            }
            pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)!!

        } else if (downloadType == DownloadType.FILE_APK) {
            val intent = Intent(context, InstallRequestBroadcastReceiver::class.java).apply {
                data = Uri.fromFile(downloadFile)
                putExtra(InstallRequestBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadOrInstallId)
            }
            pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)!!

        } else {
            val fileIntent = Utils.getIntentForFile(context, downloadFile!!, FILE_PROVIDER)

            val intent = if (fileIntent.resolveActivity(context.packageManager) == null) {
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOULD_OPEN_LIBRARY, true)
            } else {
                Intent.createChooser(fileIntent, context.resources.getString(R.string.select_app_for_file))
            }
            pendingIntent = PendingIntentCompat.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)!!
        }

        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            if (errorName != null) {
                setContentTitle(context.resources.getString(R.string.notification_download_error, fileName))
                setContentText(errorName)
            } else {
                if (downloadType == DownloadType.FILE)
                    setContentTitle(context.resources.getString(R.string.notification_download_complete_title))
                else
                    setContentTitle(context.resources.getString(R.string.notification_install_title))
                setContentText(fileName)
            }

            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)

            notify(context, downloadOrInstallId, this.build())
        }
    }

    private fun createProgressNotification(
        context: Context,
        fileName: String,
        downloadId: Long,
        uploadId: Int,
        progressPercent: Int?
    ) {
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
            setOngoing(true)
            setOnlyAlertOnce(true)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.ic_mitch_notification)
            setContentTitle(fileName)

            if (progressPercent != null)
                setProgress(100, progressPercent, false)
            else
                setProgress(100, 0, true)

            val cancelIntent = Intent(context, DownloadCancelBroadcastReceiver::class.java).apply {
                putExtra(DownloadCancelBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(DownloadCancelBroadcastReceiver.EXTRA_UPLOAD_ID, uploadId)
            }
            val cancelPendingIntent = PendingIntentCompat.getBroadcast(context, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)
            addAction(R.drawable.ic_baseline_cancel_24, context.getString(R.string.dialog_cancel),
                    cancelPendingIntent)
            notify(context, downloadId, this.build())
        }
    }

    private fun notify(context: Context, downloadId: Long, notification: Notification) {
        val tag = if (Utils.fitsInInt(downloadId))
            NOTIFICATION_TAG_DOWNLOAD
        else
            NOTIFICATION_TAG_DOWNLOAD_LONG
        MitchActivity.tryNotifyWithPermission(
            null, context,
            tag, downloadId.toInt(), notification,
            R.string.dialog_notification_explain_download
        )
    }

    protected suspend fun onCompleted(
        context: Context,
        fileName: String,
        uploadId: Int,
        downloadOrInstallId: Long,
        type: DownloadType
    ) {
        val downloadFileManager = Mitch.fileManager

        val db = AppDatabase.getDatabase(context)
        val pendingInstall = db.installDao.getPendingInstallationByDownloadId(downloadOrInstallId)!!

        val notificationFile: File? = when (type) {
            DownloadType.SESSION_INSTALL ->
                null
            DownloadType.FILE_APK ->
                downloadFileManager.getPendingFile(uploadId)
            DownloadType.FILE -> {
                Installations.deleteOutdatedInstalls(context, pendingInstall)
                downloadFileManager.replacePendingFile(uploadId)

                downloadFileManager.getDownloadedFile(uploadId)
            }
        }
        createResultNotification(context, fileName, type, notificationFile, downloadOrInstallId, null, null)
        Mitch.databaseHandler.onDownloadComplete(pendingInstall, type)
    }

    protected suspend fun onError(
        context: Context,
        fileName: String,
        downloadOrInstallId: Long,
        uploadId: Int,
        downloadType: DownloadType,
        errorName: String,
        throwable: Throwable?
    ) {
        createResultNotification(context, fileName, downloadType, null, downloadOrInstallId, errorName, Utils.toString(throwable))

        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            Mitch.fileManager.deletePendingFile(uploadId)
            Mitch.databaseHandler.onDownloadFailed(downloadOrInstallId)
        }
    }

    protected fun onProgress(context: Context, fileName: String, downloadId: Long, uploadId: Int,
                             progressPercent: Int?) {
        createProgressNotification(context, fileName, downloadId, uploadId, progressPercent)
    }

    /**
     * Quietly cancel download
     */
    protected fun onCancel(context: Context, downloadId: Long) {
        with(context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager) {
            if (Utils.fitsInInt(downloadId))
                cancel(NOTIFICATION_TAG_DOWNLOAD, downloadId.toInt())
            else
                cancel(NOTIFICATION_TAG_DOWNLOAD_LONG, downloadId.toInt())
        }
    }
}
