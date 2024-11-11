package garden.appl.mitch.files

import android.content.Context
import android.util.Log
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.install.AbstractInstaller
import garden.appl.mitch.install.Installations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     *
     * @param contentLength file size, null if unknown
     */
    suspend fun requestDownload(context: Context, url: String,
                                fileName: String, contentLength: Long?, install: Installation) {
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
        if (fileName.endsWith(".apk")) {
            val installer = Installations.getInstaller(context)

            Log.d(LOGGING_TAG, "content length: $contentLength")
            if (installer.type == AbstractInstaller.Type.Stream)
                Downloader.requestDownload(context, url, install, fileName, contentLength, null, installer)
            else
                Downloader.requestDownload(context, url, install, fileName, contentLength,  file, null)
        } else {
            Downloader.requestDownload(context, url, install, fileName, contentLength, file, null)
        }
    }

    fun checkIsDownloading(context: Context, install: Installation): Boolean {
        if (install.status != Installation.STATUS_DOWNLOADING
            && install.status != Installation.STATUS_READY_TO_INSTALL)
            return false

        return install.downloadOrInstallId?.let { downloadId ->
            Downloader.checkIsDownloading(context, downloadId)
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
        Downloader.cancel(context, downloadId)
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