package ua.gardenapple.itchupdater.installer

import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.Extras
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.File

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
     * Request to start downloading file into internal storage.
     * Once download (and/or installation) is complete,
     * the files for [replacedUploadId] will be deleted.
     * @param pendingInstall the [Installation] which we're about to download. Must have internalId = 0
     * @param url URL of file to download
     * @param contentDisposition HTTP content disposition header
     * @param mimeType MIME type
     * @param replacedUploadId upload to delete
     */
    suspend fun requestDownload(
        pendingInstall: Installation,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        replacedUploadId: Int = pendingInstall.uploadId
    ) {
        if (pendingInstall.internalId != 0)
            throw IllegalArgumentException("Pending installation already has internalId!")

        Log.d(LOGGING_TAG, "Content disposition: $contentDisposition")
        Log.d(LOGGING_TAG, "MIME type: $mimeType")
        val uploadId = pendingInstall.uploadId
        val fileName = if (mimeType == "application/octet-stream") {
            URLUtil.guessFileName(url, contentDisposition, null)
        } else {
            URLUtil.guessFileName(url, contentDisposition, mimeType)
        }

        //cancel download for current pending installation
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            var willStartDownload = false

            db.installDao.getPendingInstallation(uploadId)?.let { currentPendingInstall ->
                Log.d(LOGGING_TAG, "Already existing install for $uploadId")

                if (currentPendingInstall.status == Installation.STATUS_DOWNLOADING) {
                    Log.d(LOGGING_TAG, "Deleting")
                    willStartDownload = true
                    fetch.cancel(currentPendingInstall.downloadOrInstallId!!, { _: Download ->
                        startDownload(url, fileName, pendingInstall, replacedUploadId)
                    })
                }
            }

            if (!willStartDownload) {
                startDownload(url, fileName, pendingInstall, replacedUploadId)
            }
        }
    }

    /**
     * All previous downloads for the same uploadId must be cancelled at this point
     */
    private fun startDownload(
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
        val dir = File(pendingPath, uploadId.toString())
        dir.deleteRecursively()
    }

    fun deletePendingFile(download: Download) {
        deletePendingFile(Integer.parseInt(download.extras.getString(DOWNLOAD_EXTRA_UPLOAD_ID,
            "")))
    }
    
    fun replacePendingFile(uploadId: Int, replacedUploadId: Int) {
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
    
    fun requestCancellation(downloadId: Int) {
        fetch.cancel(downloadId, { download ->
            deletePendingFile(download)
        })
    }
    
    fun getPendingFile(uploadId: Int): File? {
        val dir = File(pendingPath, uploadId.toString())
        return dir.listFiles()?.getOrNull(0)
    }

    fun deleteDownloadedFile(uploadId: Int) {
        val dir = File(uploadsPath, uploadId.toString())
        dir.deleteRecursively()
    }
}