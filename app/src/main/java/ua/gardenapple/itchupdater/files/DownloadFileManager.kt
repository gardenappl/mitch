package ua.gardenapple.itchupdater.files

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
import java.util.*

class DownloadFileManager(private val context: Context, private val fetch: Fetch) {
    companion object {
        private const val LOGGING_TAG = "DownloadFileManager"

        const val APK_MIME = "application/vnd.android.package-archive"
        
        const val DOWNLOAD_EXTRA_UPLOAD_ID = "uploadId"
    }

    private val uploadsPath = File(context.filesDir, "upload")
    private val pendingPath = File(context.filesDir, "pending")

    
    fun setup() {
        uploadsPath.mkdirs()
        pendingPath.mkdirs()
    }

    /**
     * All previous downloads for the same uploadId must be cancelled at this point
     */
    internal fun startDownload(url: String, fileName: String, pendingInstall: Installation) {
        val uploadId = pendingInstall.uploadId
        deletePendingFile(uploadId)
        val request = Request(url, "$pendingPath/$uploadId/$fileName").apply {
            this.networkType = NetworkType.ALL
            this.extras =
                Extras(Collections.singletonMap(DOWNLOAD_EXTRA_UPLOAD_ID, uploadId.toString()))
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
                notify(NOTIFICATION_TAG_DOWNLOAD, 0, builder.build())
            }
        })
    }
    
    fun deletePendingFile(uploadId: Int) {
        if (!shouldHandleFiles(uploadId))
            return
        
        val dir = File(pendingPath, uploadId.toString())
        dir.deleteRecursively()
    }
    
    fun replacePendingFile(download: Download) {
        val uploadId = getUploadId(download)

        if (!shouldHandleFiles(uploadId))
            return

        val newPath = File(uploadsPath, uploadId.toString())
        newPath.deleteRecursively()

        val pendingPath = File(pendingPath, uploadId.toString())
        pendingPath.renameTo(newPath)
    }
    
    fun requestCancellation(downloadId: Int, uploadId: Int, callback: (() -> Unit)? = null) {
        fetch.remove(downloadId, {
            deletePendingFile(uploadId)
            callback?.invoke()
        }) { error ->
            Log.e(LOGGING_TAG, "Error while cancelling/removing download: ${error.name}",
                error.throwable)
            deletePendingFile(uploadId)
            callback?.invoke()
        }
    }

    /**
     * Remove download from Fetch's internal database
     */
    fun removeFetchDownload(downloadId: Int) {
        fetch.remove(downloadId)
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

    fun getDownloadedFile(uploadId: Int): File? {
        val dir = File(uploadsPath, uploadId.toString())
        return dir.listFiles()?.getOrNull(0)
    }
    
    fun getUploadId(download: Download): Int {
        return Integer.parseInt(download.extras.getString(DOWNLOAD_EXTRA_UPLOAD_ID, ""))
    }
    
    private fun shouldHandleFiles(uploadId: Int): Boolean {
        return !(uploadId == Installation.MITCH_UPLOAD_ID && BuildConfig.FLAVOR == FLAVOR_GITLAB)
    }
}