package ua.gardenapple.itchupdater.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.client.GameDownloader
import ua.gardenapple.itchupdater.database.AppDatabase

/**
 * This is an internal receiver which only receives broadcasts when clicking the "Update available" notification.
 */
class UpdateNotificationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "DownloadNotification"

        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_UPLOAD_ID = "UPLOAD_ID"
        const val EXTRA_DOWNLOAD_KEY = "DOWNLOAD_KEY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        val extras = intent.extras!!

        val gameId = extras.getInt(EXTRA_GAME_ID)
        val uploadId = extras.getInt(EXTRA_UPLOAD_ID)
        val downloadKey = extras.getString(EXTRA_DOWNLOAD_KEY)

        runBlocking(Dispatchers.IO) {
            val downloader = GameDownloader(context)
            val db = AppDatabase.getDatabase(context)
            val game = db.gameDao.getGameById(gameId)!!
            downloader.startDownload(game, uploadId, downloadKey)
        }
    }
}