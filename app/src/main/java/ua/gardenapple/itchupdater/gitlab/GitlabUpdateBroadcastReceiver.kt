package ua.gardenapple.itchupdater.gitlab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.client.GameDownloader
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.DownloadFileManager

/**
 * This is an internal receiver which only receives broadcasts when clicking the "Update available" notification
 * (for the GitLab version of Mitch)
 */
class GitlabUpdateBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_DOWNLOAD_URL = "DOWNLOAD_URL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL)!!

        MitchApp.downloadFileManager.requestDownload(Installation.MITCH_UPLOAD_ID,
            downloadUrl, null, DownloadFileManager.APK_MIME) {
                downloadId ->
            val downloader = GameDownloader(context)
            val db = AppDatabase.getDatabase(context)

            val install = db.installDao.getInstallationByPackageName(context.packageName)!!
            install.downloadOrInstallId = downloadId
            install.status = Installation.STATUS_DOWNLOADING
            runBlocking(Dispatchers.IO) {
                downloader.updateDatabase(null, install)
            }
        }
    }
}