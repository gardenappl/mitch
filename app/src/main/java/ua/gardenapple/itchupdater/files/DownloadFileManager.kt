package ua.gardenapple.itchupdater.files

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ua.gardenapple.itchupdater.NOTIFICATION_CHANNEL_ID_INSTALLING
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.File

class DownloadFileManager(context: Context, private val fetchDownloader: FetchDownloader) {
//    companion object {
//        const val APK_MIME = "application/vnd.android.package-archive"
//    }

    private val uploadsPath = File(context.filesDir, "upload")
    private val pendingPath = File(context.filesDir, "pending")

    
    fun setup() {
        uploadsPath.mkdirs()
        pendingPath.mkdirs()
    }

    /**
     * All previous downloads for the same uploadId must be cancelled at this point
     */
    suspend fun requestDownload(context: Context, url: String,
                                fileName: String, install: Installation) {
        val uploadId = install.uploadId
        deletePendingFile(uploadId)

        val errorString =
            getDownloaderForInstall(install).requestDownload(context, url, fileName, install)

        if (errorString != null) {
            val builder =
                NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                    setContentTitle(install.uploadName)
                    setContentText(
                        context.resources.getString(
                            R.string.notification_download_error,
                            errorString
                        )
                    )
                    priority = NotificationCompat.PRIORITY_HIGH
                }
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_TAG_DOWNLOAD, 0, builder.build())
            }
        }
    }

    suspend fun checkIsDownloading(downloadId: Int): Boolean {
        return getDownloaderForId(downloadId).checkIsDownloading(downloadId)
    }
    
    fun deletePendingFile(uploadId: Int) {
        val dir = File(pendingPath, uploadId.toString())
        dir.deleteRecursively()
    }
    
    fun replacePendingFile(uploadId: Int) {
        val newPath = File(uploadsPath, uploadId.toString())
        newPath.deleteRecursively()

        val pendingPath = File(pendingPath, uploadId.toString())
        pendingPath.renameTo(newPath)
    }

    suspend fun requestCancel(downloadId: Int, uploadId: Int) {
        getDownloaderForId(downloadId).requestCancel(downloadId)
        deletePendingFile(uploadId)
    }
    
    fun getPendingFile(uploadId: Int): File? {
        val dir = File(pendingPath, uploadId.toString())
        return dir.listFiles()?.getOrNull(0)
    }

    fun deleteDownloadedFile(uploadId: Int) {
        val dir = File(uploadsPath, uploadId.toString())
        dir.deleteRecursively()
    }

    fun getDownloadedFile(uploadId: Int): File? {
        val dir = File(uploadsPath, uploadId.toString())
        return dir.listFiles()?.getOrNull(0)
    }

    private fun getDownloaderForInstall(install: Installation): AbstractDownloader {
        return fetchDownloader
    }

    private fun getDownloaderForId(downloadId: Int): AbstractDownloader {
        return fetchDownloader
    }
}