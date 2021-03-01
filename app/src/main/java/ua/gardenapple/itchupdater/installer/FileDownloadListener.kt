package ua.gardenapple.itchupdater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2core.DownloadBlock
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.ui.MainActivity
import java.io.File

/**
 * This listener responds to finished file downloads from Fetch.
 */
class FileDownloadListener(private val context: Context) : FetchListener {
    companion object {
        private const val LOGGING_TAG = "FileDownloadListener"
    }

    private fun createResultNotification(context: Context,
                                         downloadFile: File,
                                         id: Int,
                                         isApk: Boolean,
                                         error: Error? = null) {
        val pendingIntent: PendingIntent?
        if (error != null) {
            pendingIntent = null
        } else if (isApk) {
            Log.d(LOGGING_TAG, downloadFile.path)

            val intent = Intent(context, InstallNotificationBroadcastReceiver::class.java).apply {
                data = Uri.fromFile(downloadFile)
                putExtra(InstallNotificationBroadcastReceiver.EXTRA_DOWNLOAD_ID, id)
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
            if (error != null) {
                setContentTitle(downloadFile.name)
                setContentText(context.resources.getString(R.string.notification_download_error, error.name))
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
            notify(NOTIFICATION_TAG_DOWNLOAD, id, builder.build())
        }
    }
    
    private fun createProgressNotification(context: Context, download: Download,
                                           etaInMilliSeconds: Long?) {
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
            setOngoing(true)
            setOnlyAlertOnce(true)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.ic_mitch_notification)
            setContentTitle(download.fileUri.lastPathSegment)

            setProgress(100, download.progress, etaInMilliSeconds == null)
            if (etaInMilliSeconds != null)
                setContentInfo(context.resources.getString(R.string.notification_download_time_remaining,
                etaInMilliSeconds / 60_000, etaInMilliSeconds / 1000))

            val cancelIntent = Intent(context, DownloadCancelBroadcastReceiver::class.java).apply {
                putExtra(DownloadCancelBroadcastReceiver.EXTRA_DOWNLOAD_ID, download.id)
                val uploadId = Mitch.fileManager.getUploadId(download)
                putExtra(DownloadCancelBroadcastReceiver.EXTRA_UPLOAD_ID, uploadId)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            addAction(R.drawable.ic_baseline_cancel_24, context.getString(R.string.dialog_cancel),
                cancelPendingIntent)
        }
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_TAG_DOWNLOAD, download.id, builder.build())
        }
    }

    override fun onAdded(download: Download) {}

    override fun onCancelled(download: Download) {
        Log.d(LOGGING_TAG, "Cancelled ID: ${download.id}")
        Mitch.fileManager.removeFetchDownload(download.id)
    }

    override fun onCompleted(download: Download) {
        val isApk = download.file.endsWith(".apk")
        val downloadFileManager = Mitch.fileManager
        val uploadId = downloadFileManager.getUploadId(download)

        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val pendingInstall = db.installDao.getPendingInstallationByDownloadId(download.id)!!

            val notificationFile: File
            if (isApk) {
                notificationFile = downloadFileManager.getPendingFile(uploadId)!!
            } else {
                Installations.deleteOutdatedInstalls(context, pendingInstall)
                downloadFileManager.replacePendingFile(download)
                notificationFile = downloadFileManager.getDownloadedFile(uploadId)!!
            }
            createResultNotification(context, notificationFile, download.id, isApk)
            Mitch.databaseHandler.onDownloadComplete(pendingInstall, isApk)
        }
        downloadFileManager.removeFetchDownload(download.id)
    }

    override fun onDeleted(download: Download) {}

    override fun onDownloadBlockUpdated(
        download: Download,
        downloadBlock: DownloadBlock,
        totalBlocks: Int
    ) {}

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val isApk = download.file.endsWith(".apk")
        createResultNotification(context, File(download.file), download.id, isApk, error)

        runBlocking(Dispatchers.IO) {
            val uploadId = Mitch.fileManager.getUploadId(download)
            Mitch.fileManager.deletePendingFile(uploadId)
            Mitch.databaseHandler.onDownloadFailed(download.id)
        }
        Mitch.fileManager.removeFetchDownload(download.id)
    }

    override fun onPaused(download: Download) {}

    override fun onProgress(
        download: Download,
        etaInMilliSeconds: Long,
        downloadedBytesPerSecond: Long
    ) {
        createProgressNotification(context, download, etaInMilliSeconds)
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {}

    override fun onRemoved(download: Download) {}

    override fun onResumed(download: Download) {}

    override fun onStarted(
        download: Download,
        downloadBlocks: List<DownloadBlock>,
        totalBlocks: Int
    ) {
        createProgressNotification(context, download, null)
    }

    override fun onWaitingNetwork(download: Download) {}
}