package ua.gardenapple.itchupdater.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.Mitch
import java.io.File

/**
 * This is an internal receiver which only receives broadcasts when clicking the "Click to install" notification.
 */
class InstallRequestBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "InstallRequestReceiver"

        const val EXTRA_DOWNLOAD_ID = "DOWNLOAD_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        val extras = intent.extras!!

        val downloadId = extras.getLong(EXTRA_DOWNLOAD_ID)

        runBlocking {
            val apkFile = File(intent.data!!.path!!)
            val installer = Installations.getInstaller(context)
            installer.requestInstall(context, downloadId, apkFile)
        }
    }

}