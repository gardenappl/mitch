package ua.gardenapple.itchupdater.installer

import android.app.DownloadManager
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
import ua.gardenapple.itchupdater.ui.MainActivity
import java.io.File
import java.net.URLConnection

/**
 * This listener responds to finished file downloads from Fetch.
 */
class FileDownloadListener(private val context: Context) : FetchListener {

    private fun createNotification(context: Context,
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
            val fileIntent = Utils.getIntentForFile(context, downloadFile)

            val intent = if (fileIntent.resolveActivity(context.packageManager) == null) {
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOULD_OPEN_LIBRARY, true)
            } else {
                Intent.createChooser(fileIntent, context.resources.getString(R.string.select_app_for_file))
            }
            pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL).apply {
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
            notify(NOTIFICATION_TAG_DOWNLOAD_RESULT, id, builder.build())
        }
    }

    override fun onAdded(download: Download) {
    }

    override fun onCancelled(download: Download) {
    }

    override fun onCompleted(download: Download) {
        val isApk = download.file.endsWith(".apk")
        val downloadFileManager = MitchApp.downloadFileManager

        runBlocking(Dispatchers.IO) {
            if (!isApk) {
                downloadFileManager.replacePendingFile(download)
            }
            val uploadId = downloadFileManager.getUploadId(download)
            createNotification(context, downloadFileManager.getDownloadedFile(uploadId)!!,
                download.id, isApk)
            MitchApp.installerDatabaseHandler.onDownloadComplete(download.id, isApk)
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
        createNotification(context, File(download.file), download.id, isApk, error)

        runBlocking(Dispatchers.IO) {
            val uploadId = MitchApp.downloadFileManager.getUploadId(download)
            MitchApp.downloadFileManager.deletePendingFile(uploadId)
            MitchApp.installerDatabaseHandler.onDownloadFailed(download.id)
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