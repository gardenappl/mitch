package garden.appl.mitch.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import garden.appl.mitch.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * This is an internal receiver which only receives broadcasts when clicking
 * the "Update available" notification, and there is one single uploadId available to install.
 */
class UpdateNotificationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "DownloadNotification"

        const val EXTRA_INSTALL_ID = "INSTALL_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        val extras = intent.extras!!

        val installId = extras.getInt(EXTRA_INSTALL_ID)

        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val updateCheckResult = db.updateCheckDao.getUpdateCheckResult(installId)!!
            GameDownloader.startUpdate(context, updateCheckResult)
        }
    }
}