package garden.appl.mitch.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import garden.appl.mitch.Utils
import garden.appl.mitch.install.InstallRequestBroadcastReceiver.Companion.EXTRA_DOWNLOAD_ID
import garden.appl.mitch.install.InstallRequestBroadcastReceiver.Companion.EXTRA_STREAM_SESSION_ID
import java.io.File

/**
 * This is an internal receiver which only receives broadcasts when clicking the "Click to install" notification.
 *
 * Supply either one of [EXTRA_DOWNLOAD_ID] (for [AbstractInstaller.Type.File] )
 * or [EXTRA_STREAM_SESSION_ID] (for [AbstractInstaller.Type.Stream])
 */
class InstallRequestBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "InstallRequestReceiver"

        const val EXTRA_DOWNLOAD_ID = "DOWNLOAD_ID"
        const val EXTRA_STREAM_SESSION_ID = "stream_id"
        const val EXTRA_APP_NAME = "app_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        val extras = intent.extras!!

        Utils.getLong(extras, EXTRA_DOWNLOAD_ID)?.let { downloadId ->
            val apkFile = File(intent.data!!.path!!)

            val installer = Installations.getInstaller(downloadId)
            assert(installer.type == AbstractInstaller.Type.File)

            runBlocking(Dispatchers.IO) {
                installer.requestInstall(context, downloadId, apkFile)
            }
            return@onReceive
        }
        Utils.getLong(extras, EXTRA_STREAM_SESSION_ID)?.let { sessionId ->
            val installer = Installations.getInstaller(sessionId)
            assert(installer.type == AbstractInstaller.Type.Stream)

            val appName = extras.getString(EXTRA_APP_NAME)!!

            runBlocking(Dispatchers.IO) {
                installer.finishStreamInstall(context, sessionId.toInt(), appName)
            }
            return@onReceive
        }
        throw IllegalStateException("Must provide either EXTRA_DOWNLOAD_ID or EXTRA_STREAM_SESSION_ID")
    }
}