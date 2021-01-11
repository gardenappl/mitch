package ua.gardenapple.itchupdater.client

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.Extras
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.File
import java.util.function.Function

class DownloadFileManager(private val context: Context, private val fetch: Fetch) {
    companion object {
        private const val LOGGING_TAG = "DownloadRequester"

        const val APK_MIME = "application/vnd.android.package-archive"
        
        const val DOWNLOAD_EXTRA_UPLOAD_ID = "uploadId"
        const val DOWNLOAD_EXTRA_REPLACED_UPLOAD_ID = "uploadIdReplaced"
    }

    private val uploadsPath = File(context.filesDir, "upload")
    private val pendingPath = File(context.filesDir, "pending")


    /**
     * All previous downloads for the same uploadId must be cancelled at this point
     */
    internal fun startDownload(
        url: String,
        fileName: String,
        pendingInstall: Installation,
        replacedUploadId: Int
    ) {
        val uploadId = pendingInstall.uploadId
        deletePendingFile(uploadId)
        val request = Request(url, "$pendingPath/$uploadId/$fileName").apply {
            this.networkType = NetworkType.ALL
            this.extras = Extras(mapOf(
                DOWNLOAD_EXTRA_UPLOAD_ID to uploadId.toString(),
                DOWNLOAD_EXTRA_REPLACED_UPLOAD_ID to replacedUploadId.toString()
            ))
        }

        fetch.enqueue(request, { updatedRequest ->
            Log.d(LOGGING_TAG, "Enqueued ${updatedRequest.id}")
            pendingInstall.downloadOrInstallId = updatedRequest.id
            pendingInstall.status = Installation.STATUS_DOWNLOADING
            runBlocking(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                db.installDao.insert(pendingInstall)
            }
        }, { error ->
            Log.e(LOGGING_TAG, error.name, error.throwable)
            val builder =
                NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                    setContentTitle(pendingInstall.uploadName)
                    setContentText(context.resources.getString(R.string.notification_download_error, error.name))
                    priority = NotificationCompat.PRIORITY_HIGH
                }
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_TAG_DOWNLOAD_RESULT, 0, builder.build())
            }
        })
    }
    
    fun deletePendingFile(uploadId: Int) {
        if (!shouldHandleFiles(uploadId))
            return
        
        val dir = File(pendingPath, uploadId.toString())
        dir.deleteRecursively()
    }

    fun deletePendingFile(download: Download) {
        deletePendingFile(Integer.parseInt(download.extras.getString(
            DOWNLOAD_EXTRA_UPLOAD_ID,
            "")))
    }
    
    fun replacePendingFile(uploadId: Int, replacedUploadId: Int) {
        if (!shouldHandleFiles(uploadId))
            return
        
        val replacedPath = File(uploadsPath, replacedUploadId.toString())
        replacedPath.deleteRecursively()

        val pendingPath = File(pendingPath, uploadId.toString())
        val newPath = File(uploadsPath, uploadId.toString())
        pendingPath.renameTo(newPath)
    }
    
    fun replacePendingFile(download: Download) {
        val uploadId = Integer.parseInt(download.extras.getString(
            DOWNLOAD_EXTRA_UPLOAD_ID, ""))
        val replacedUploadId = Integer.parseInt(download.extras.getString(
            DOWNLOAD_EXTRA_REPLACED_UPLOAD_ID, ""))
        replacePendingFile(uploadId, replacedUploadId)
    }
    
    fun requestCancellation(downloadId: Int, callback: ((Download) -> Unit)? = null) {
        fetch.cancel(downloadId, { download ->
            deletePendingFile(download)
            callback?.invoke(download)
        })
    }
    
    fun getPendingFile(uploadId: Int): File? {
        val dir = File(pendingPath, uploadId.toString())
        return dir.listFiles()?.getOrNull(0)
    }

    fun deleteDownloadedFile(uploadId: Int) {
        if (!shouldHandleFiles(uploadId))
            return
        val dir = File(uploadsPath, uploadId.toString())
        dir.deleteRecursively()
    }
    
    private fun shouldHandleFiles(uploadId: Int): Boolean {
        return !(uploadId == Installation.MITCH_UPLOAD_ID && BuildConfig.FLAVOR == FLAVOR_GITLAB)
    }
}