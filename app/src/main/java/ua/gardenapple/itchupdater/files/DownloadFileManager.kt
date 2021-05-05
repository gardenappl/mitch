package ua.gardenapple.itchupdater.files

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.client.GameDownloader
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.Installations
import java.io.File

class DownloadFileManager(context: Context, private val fetchDownloader: DownloaderFetch) {
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
    suspend fun requestDownload(context: Context, url: String, fileName: String,
                                install: Installation) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        //cancel download for current pending installation
        db.installDao.getPendingInstallation(install.uploadId)?.let { currentPendingInstall ->
            Log.d(LOGGING_TAG, "Already existing install for ${install.uploadId}")

            Installations.cancelPending(context, currentPendingInstall)
        }

        val uploadId = install.uploadId
        deletePendingFile(uploadId)

        val file = File(File(pendingPath, uploadId.toString()), fileName)
        val errorString =
            getDownloaderForInstall(context, install).requestDownload(context, url, file, install)

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

    suspend fun checkIsDownloading(context: Context, install: Installation): Boolean {
        return install.downloadOrInstallId?.let { downloadId ->
            getDownloaderForId(downloadId).checkIsDownloading(context, downloadId.toInt())
        } ?: false
    }

    fun deleteAllDownloads(context: Context) {
        Mitch.workerDownloader.cancelAll(context)
        fetchDownloader.deleteAllDownloads()
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
        getDownloaderForId(downloadId).cancel(context, downloadId.toInt())
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

    private fun getDownloaderForInstall(context: Context,
                                        install: Installation): DownloaderAbstract {
        if (install.status == Installation.STATUS_INSTALLING)
            throw IllegalArgumentException("Tried to get Downloader for INSTALLING $install")

        val downloadId = install.downloadOrInstallId
        if (downloadId == null) {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            return if (sharedPrefs.getString(PREF_DOWNLOADER, "standard") == "standard")
                Mitch.workerDownloader
            else
                fetchDownloader
        }

        return getDownloaderForId(downloadId)
    }

    private fun getDownloaderForId(downloadId: Long): DownloaderAbstract {
        return if (Utils.fitsInInt(downloadId))
            fetchDownloader
        else
            Mitch.workerDownloader
    }
}