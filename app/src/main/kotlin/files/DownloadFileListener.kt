package garden.appl.mitch.files

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_INSTALLING
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD_LONG
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.ui.MitchActivity

open class DownloadFileListener {
    open suspend fun onCompleted(context: Context, fileName: String, uploadId: Int?,
                            downloadOrInstallId: Long, type: DownloadType) {
        Log.d("haaaa", "stub!")
    }

    open suspend fun onError(context: Context, fileName: String, uploadId: Int?,
                             downloadOrInstallId: Long, type: DownloadType, errorName: String,
                             throwable: Throwable?) {

    }

    open fun onProgress(context: Context, fileName: String, downloadId: Long, progressPercent: Int?) {

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

    protected fun createProgressNotification(
        context: Context,
        fileName: String,
        downloadId: Long,
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