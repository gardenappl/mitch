package ua.gardenapple.itchupdater.gitlab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.GameDownloader
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.installer.DownloadRequester

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

        runBlocking(Dispatchers.IO) {
            DownloadRequester.requestDownload(context, null, downloadUrl,
                null, DownloadRequester.APK_MIME)
        }
    }
}