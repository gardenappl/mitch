package ua.gardenapple.itchupdater.installer

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD_LONG
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase

/**
 * This is an internal receiver which only receives broadcasts when clicking "Cancel"
 * on a download notification
 */
class DownloadCancelBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "DownloadCancelReceiver"

        const val EXTRA_DOWNLOAD_ID = "DOWNLOAD_ID"
        const val EXTRA_UPLOAD_ID = "UPLOAD_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = Utils.getLong(intent.extras!!, EXTRA_DOWNLOAD_ID)!!
        Log.d(LOGGING_TAG, "downloadId: $downloadId")
        val uploadId = Utils.getInt(intent.extras!!, EXTRA_UPLOAD_ID)!!
        Log.d(LOGGING_TAG, "uploadId: $uploadId")

        val notificationManager =
            context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
        if (Utils.fitsInInt(downloadId))
            notificationManager.cancel(NOTIFICATION_TAG_DOWNLOAD, downloadId.toInt())
        else
            notificationManager.cancel(NOTIFICATION_TAG_DOWNLOAD_LONG, downloadId.toInt())

        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.installDao.getPendingInstallationByDownloadId(downloadId)?.let {
                Installations.cancelPending(context, it)
            }
        }
    }
}