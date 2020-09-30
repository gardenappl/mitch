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
        const val LOGGING_TAG = "GitlabNotification"

        const val DOWNLOAD_URL = "https://gitlab.com/gardenappl/mitch/-/raw/version-check/app-gitlab-release.apk"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")

        runBlocking(Dispatchers.IO) {
            DownloadRequester.requestDownload(
                context, null, DOWNLOAD_URL, null, DownloadRequester.APK_MIME
            )
        }
    }
}