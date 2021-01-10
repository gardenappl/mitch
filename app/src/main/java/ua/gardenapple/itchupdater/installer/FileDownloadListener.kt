package ua.gardenapple.itchupdater.installer

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import java.io.File

/**
 * This listener responds to finished file downloads from Fetch.
 */
class FileDownloadListener(private val context: Context) : FetchListener {

    private fun createNotification(context: Context,
                                   downloadLocalUri: Uri, 
                                   id: Int,
                                   isApk: Boolean, 
                                   error: Error? = null) {
        val pendingIntent: PendingIntent?
        if (error != null) {
            pendingIntent = null
        } else if (isApk) {
            val downloadPath = downloadLocalUri.path!!
            Log.d(LOGGING_TAG, downloadPath)

            val intent = Intent(context, InstallNotificationBroadcastReceiver::class.java).apply {
                data = Uri.parse(downloadPath)
                putExtra(InstallNotificationBroadcastReceiver.EXTRA_DOWNLOAD_ID, id)
            }
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
        }

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            if (error != null)
                setContentTitle(context.resources.getString(R.string.notification_download_error_title))
            else if (isApk)
                setContentTitle(context.resources.getString(R.string.notification_install_title))
            else
                setContentTitle(context.resources.getString(R.string.notification_download_complete_title))

            setContentText(downloadLocalUri.lastPathSegment)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_TAG_DOWNLOAD_RESULT, id, builder.build())
        }
    }

    override fun onAdded(download: Download) {
    }

    override fun onCancelled(download: Download) {
        val isApk = download.file.endsWith(".apk")

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                MitchApp.downloadFileManager.deletePendingFiles(download)
            }
            InstallerEvents.notifyDownloadComplete(download.id, isApk)
        }
    }

    override fun onCompleted(download: Download) {
        val isApk = download.file.endsWith(".apk")
        createNotification(context, download.fileUri, download.id, isApk)

        GlobalScope.launch {
            if (!isApk) {
                withContext(Dispatchers.IO) {
                    MitchApp.downloadFileManager.movePendingFiles(download)
                }
            }

            InstallerEvents.notifyDownloadComplete(download.id, isApk)
        }
    }

    override fun onDeleted(download: Download) {
    }

    override fun onDownloadBlockUpdated(
        download: Download,
        downloadBlock: DownloadBlock,
        totalBlocks: Int
    ) {
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        val isApk = download.file.endsWith(".apk")
        createNotification(context, download.fileUri, download.id, isApk, error)

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                MitchApp.downloadFileManager.deletePendingFiles(download)
            }
            InstallerEvents.notifyDownloadFailed(download.id)
        }
    }

    override fun onPaused(download: Download) {
    }

    override fun onProgress(
        download: Download,
        etaInMilliSeconds: Long,
        downloadedBytesPerSecond: Long
    ) {
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
    }

    override fun onRemoved(download: Download) {
    }

    override fun onResumed(download: Download) {
    }

    override fun onStarted(
        download: Download,
        downloadBlocks: List<DownloadBlock>,
        totalBlocks: Int
    ) {
    }

    override fun onWaitingNetwork(download: Download) {
    }
}