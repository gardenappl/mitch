package ua.gardenapple.itchupdater.installer

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.Extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.ui.PermissionRequestActivity
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

typealias OnDownloadStartListener = (downloadId: Int) -> Unit

class DownloadFileManager(private val context: Context, private val fetch: Fetch) {
    companion object {
        private const val LOGGING_TAG = "DownloadRequester"

        const val APK_MIME = "application/vnd.android.package-archive"
        
        const val DOWNLOAD_EXTRA_UPLOAD_ID = "uploadId"
    }

    /**
     * Request to start downloading file into internal storage.
     * @param uploadId upload ID of downloaded file
     * @param url URL of file to download
     * @param contentDisposition HTTP content disposition header
     * @param mimeType MIME type
     * @param callback function to run once the download has been enqueued.
     */
    fun requestDownload(
        uploadId: Int,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        callback: OnDownloadStartListener? = null
    ) {
        
        GlobalScope.launch(Dispatchers.IO) {
            //cancel download for current uploadId
            val db = AppDatabase.getDatabase(context)
            db.installDao.getPendingInstallation(uploadId)?.let { currentPendingInstall ->
                Log.d(LOGGING_TAG, "Already existign install for $uploadId")
                if (currentPendingInstall.status == Installation.STATUS_DOWNLOADING) {
                    Log.d(LOGGING_TAG, "Deleting")
                    fetch.cancel(currentPendingInstall.downloadOrInstallId!!)
                }
            }

            deletePendingFiles(uploadId)

            val fileName = if (contentDisposition == "application/octet-stream") {
                URLUtil.guessFileName(url, contentDisposition, null)
            } else {
                URLUtil.guessFileName(url, contentDisposition, contentDisposition)
            }
            val request = Request(url, "$pendingPath/$uploadId/$fileName").apply {
                this.networkType = NetworkType.ALL
                this.extras = Extras(Collections.singletonMap(DOWNLOAD_EXTRA_UPLOAD_ID, uploadId.toString()))
            }

            fetch.enqueue(request, { updatedRequest ->
                Log.d(LOGGING_TAG, "Enqueued ${updatedRequest.id}")
                callback?.invoke(updatedRequest.id)
            }, { error ->
                Log.e(LOGGING_TAG, error.name, error.throwable)
                val builder =
                    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                        setContentTitle(context.resources.getString(R.string.notification_download_error_title))
                        setContentText(error.name)
                        priority = NotificationCompat.PRIORITY_HIGH
                    }
                with(NotificationManagerCompat.from(context)) {
                    notify(NOTIFICATION_TAG_DOWNLOAD_RESULT, 0, builder.build())
                }
            })
        }
    }
    
    fun deletePendingFiles(uploadId: Int) {
        val path = File(pendingPath, uploadId.toString())
        path.deleteRecursively()
    }

    fun deletePendingFiles(download: Download) {
        deletePendingFiles(Integer.parseInt(download.extras.getString(DOWNLOAD_EXTRA_UPLOAD_ID,
            "")))
    }
    
    fun movePendingFiles(uploadId: Int) {
        val currentPath = File(uploadsPath, uploadId.toString())
        currentPath.deleteRecursively()
        val pendingPath = File(pendingPath, uploadId.toString())
        pendingPath.renameTo(currentPath)
    }
    
    fun movePendingFiles(download: Download) {
        val uploadId = Integer.parseInt(download.extras.getString(DOWNLOAD_EXTRA_UPLOAD_ID, ""))
        movePendingFiles(uploadId)
    }
    
    val uploadsPath = File(context.filesDir, "upload")
    val pendingPath = File(context.filesDir, "pending")
}