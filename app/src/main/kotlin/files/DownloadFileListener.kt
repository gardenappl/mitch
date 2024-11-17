package garden.appl.mitch.files

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import garden.appl.mitch.ErrorReportBroadcastReceiver
import garden.appl.mitch.Mitch
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_INSTALLING
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD_LONG
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.ui.MitchActivity
import java.io.File

open class DownloadFileListener {
    open suspend fun onCompleted(context: Context, fileName: String, uploadId: Int?,
                            downloadOrInstallId: Long, type: DownloadType) {
        val path = Downloader.getNormalDownloadPath(context, downloadOrInstallId)
        val file = File(path, fileName)
        val (newUri, fileName) = Mitch.externalFileManager.doMoveToDownloads(context, file)
        if (newUri == null) {
            Toast.makeText(context, R.string.dialog_missing_file_title, Toast.LENGTH_LONG)
                .show()
            return
        }
        path.deleteRecursively()

        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            setContentTitle(context.resources.getString(R.string.notification_download_complete_title))
            setContentText(fileName)

            priority = NotificationCompat.PRIORITY_HIGH
            setAutoCancel(true)

            Mitch.externalFileManager.getViewIntent(context, newUri)?.let { viewIntent ->
                val intent = Intent.createChooser(viewIntent, context.resources.getString(R.string.select_app_for_file))
                setContentIntent(PendingIntentCompat.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT, false
                ))
            }

            this@DownloadFileListener.notify(context, downloadOrInstallId, this.build())
        }
    }

    open suspend fun onError(context: Context, fileName: String, uploadId: Int?,
                             downloadOrInstallId: Long, type: DownloadType, errorName: String,
                             throwable: Throwable?) {
        val intent = Intent(context, ErrorReportBroadcastReceiver::class.java).apply {
            putExtra(ErrorReportBroadcastReceiver.EXTRA_ERROR_STRING, Utils.toString(throwable))
        }
        val pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT, false)!!
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            setContentTitle(context.resources.getString(R.string.notification_download_error, fileName))
            setContentText(errorName)

            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)

            notify(context, downloadOrInstallId, this.build())
        }
    }

    /**
     * Quietly cancel download
     */
    fun onCancel(context: Context, downloadId: Long) {
        with(context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager) {
            if (Utils.fitsInInt(downloadId))
                cancel(NOTIFICATION_TAG_DOWNLOAD, downloadId.toInt())
            else
                cancel(NOTIFICATION_TAG_DOWNLOAD_LONG, downloadId.toInt())
        }
    }


    fun onProgress(context: Context, fileName: String, downloadId: Long, progressPercent: Int?) {
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
            }
            val cancelPendingIntent = PendingIntentCompat.getBroadcast(context, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)
            addAction(
                R.drawable.ic_baseline_cancel_24, context.getString(R.string.dialog_cancel),
                cancelPendingIntent)
            notify(context, downloadId, this.build())
        }
    }

    protected fun notify(context: Context, downloadId: Long, notification: Notification) {
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
}