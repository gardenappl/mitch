package ua.gardenapple.itchupdater.client

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Update
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.InstallNotificationBroadcastReceiver
import ua.gardenapple.itchupdater.installer.UpdateNotificationBroadcastReceiver
import ua.gardenapple.itchupdater.ui.MainActivity

class WebUpdateCheckWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val LOGGING_TAG = "WebUpdateCheckWorker"
    }

    override suspend fun doWork(): Result = coroutineScope {
        val db = AppDatabase.getDatabase(context)
        val installations = db.installDao.getFinishedInstallationsSync()
        val updateChecker = WebUpdateChecker(db)
        var success = true

        coroutineScope {
            for (install in installations) {
                launch(Dispatchers.IO) {
                    val game = db.gameDao.getGameById(install.gameId)!!
                    try {
                        val result = updateChecker.checkUpdates(game.gameId)
                        handleNotification(game, install, result)

                    } catch (e: Exception) {
                        Log.e(LOGGING_TAG, "Error occurred while checking updates", e)
                        handleNotification(game, install, null)
                        success = false
                    }
                }
            }
        }

        if(success)
            Result.success()
        else
            Result.failure()
    }

    /**
     * @param result the result of the update check, can be null if the update check failed (due to network error or parsing error)
     */
    private fun handleNotification(game: Game, install: Installation, result: UpdateCheckResult?) {
        if(result?.code == UpdateCheckResult.UP_TO_DATE)
            return

        val message = when (result?.code) {
            UpdateCheckResult.UPDATE_NEEDED -> context.resources.getString(R.string.notification_update_available)
            UpdateCheckResult.EMPTY -> context.resources.getString(R.string.notification_update_empty)
            UpdateCheckResult.ACCESS_DENIED -> context.resources.getString(R.string.notification_update_access_denied)
            UpdateCheckResult.UNKNOWN -> context.resources.getString(R.string.notification_update_unknown)
            else -> context.resources.getString(R.string.notification_update_fail)
        }
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                setSmallIcon(R.drawable.ic_file_download_black_24dp)
                setContentTitle(game.name)
                setContentText(message)
                setAutoCancel(true)

                if (install.packageName != null) {
                    val icon = context.packageManager.getApplicationIcon(install.packageName)
                    setLargeIcon(Utils.drawableToBitmap(icon))
                }

                priority = NotificationCompat.PRIORITY_LOW

                val pendingIntent: PendingIntent

                if (result?.code == UpdateCheckResult.UPDATE_NEEDED) {
                    if (result.uploadID != null) {
                        val intent = Intent(context, UpdateNotificationBroadcastReceiver::class.java).apply {
                            putExtra(UpdateNotificationBroadcastReceiver.EXTRA_GAME_ID, game.gameId)
                            putExtra(UpdateNotificationBroadcastReceiver.EXTRA_UPLOAD_ID, result.uploadID)
                            val downloadKey = result.downloadPageUrl?.getDownloadKey()
                            if(downloadKey != null)
                                putExtra(UpdateNotificationBroadcastReceiver.EXTRA_DOWNLOAD_KEY, downloadKey)
                        }
                        pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    } else if(game.downloadPageUrl == null) {
                        val activityIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(game.storeUrl).run {
                                val builder = this.buildUpon()
                                builder.appendPath("purchase")
                                builder.build()
                            },
                            context,
                            MainActivity::class.java
                        )
                        pendingIntent = PendingIntent.getActivity(context, 0, activityIntent, 0)
                    } else {
                        val activityIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(game.downloadPageUrl),
                            context,
                            MainActivity::class.java
                        )
                        pendingIntent = PendingIntent.getActivity(context, 0, activityIntent, 0)
                    }
                } else {
                    val activityIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(game.storeUrl),
                        context,
                        MainActivity::class.java
                    )
                    pendingIntent = PendingIntent.getActivity(context, 0, activityIntent, 0)
                }

                setContentIntent(pendingIntent)
            }

        with(NotificationManagerCompat.from(context)) {
            //TODO: better system for notification IDs
            notify(NOTIFICATION_ID_UPDATE_CHECK + game.gameId, builder.build())
        }
    }
}