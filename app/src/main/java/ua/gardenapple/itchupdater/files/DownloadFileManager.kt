package ua.gardenapple.itchupdater.files

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.NOTIFICATION_CHANNEL_ID_INSTALLING
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.install.Installations
import java.io.File

class DownloadFileManager(context: Context) {
    companion object {
//        const val APK_MIME = "application/vnd.android.package-archive"
        const val LOGGING_TAG = "DownloadFileManager"
    }

    private val uploadsPath = File(context.filesDir, "upload")
    private val pendingPath = File(context.filesDir, "pending")

    
    fun setup() {
        uploadsPath.mkdirs()
        pendingPath.mkdirs()
    }

    /**
     * Direct request to start downloading a file.
     * Any pending installs for the same uploadId will be cancelled.
     */
    suspend fun requestDownload(context: Context, url: String,
                                fileName: String, install: Installation) {
        val db = AppDatabase.getDatabase(context)

        //cancel download for current pending installation
        db.installDao.getPendingInstallation(install.uploadId)?.let { currentPendingInstall ->
            Log.d(LOGGING_TAG, "Already existing install for ${install.uploadId}")

            Installations.cancelPending(context, currentPendingInstall)
        }

        val uploadId = install.uploadId
        withContext(Dispatchers.IO) {
            deletePendingFile(uploadId)
        }

        val file = File(File(pendingPath, uploadId.toString()), fileName)
        val errorString = Downloader.requestDownload(context, url, file, install)

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

    fun checkIsDownloading(context: Context, install: Installation): Boolean {
        if (install.status != Installation.STATUS_DOWNLOADING
            && install.status != Installation.STATUS_READY_TO_INSTALL)
            return false

        return install.downloadOrInstallId?.let { downloadId ->
            Downloader.checkIsDownloading(context, downloadId.toInt())
        } ?: false
    }

    fun deleteAllDownloads(context: Context) {
        Downloader.cancelAll(context)
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

    suspend fun cancel(context: Context, downloadId: Long, uploadId: Int) {
        Downloader.cancel(context, downloadId.toInt())
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
}