package ua.gardenapple.itchupdater.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.MitchApp

/**
 * This is an internal receiver which only receives broadcasts when clicking the "Click to install" notification.
 */
class InstallNotificationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val LOGGING_TAG = "InstallReceiver"
        const val EXTRA_DOWNLOAD_ID = "DOWNLOAD_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        val extras = intent.extras!!

        val downloadId = extras.getLong(EXTRA_DOWNLOAD_ID)

        val sessionID = MitchApp.installer.createSession(context)
        Log.d(LOGGING_TAG, "Created session")
        InstallerEvents.notifyDownloadComplete(downloadId, sessionID)
        Log.d(LOGGING_TAG, "Notified")
        runBlocking {
            MitchApp.installer.install(intent.data!!, sessionID, context)
        }
        Log.d(LOGGING_TAG, "Installed")
    }

}