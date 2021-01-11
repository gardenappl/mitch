package ua.gardenapple.itchupdater.client

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.NOTIFICATION_CHANNEL_ID_INSTALLING
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_UPDATE_CHECK
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.UpdateNotificationBroadcastReceiver
import ua.gardenapple.itchupdater.ui.MainActivity

class UpdateCheckWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val LOGGING_TAG = "UpdateCheckWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val installations = db.installDao.getFinishedInstallationsSync()
        val updateChecker = UpdateChecker(db)
        var success = true

        coroutineScope {
            //We support multiple install per game, and we don't want to download the
            //HTML for the same game multiple times
            val gameCache = HashMap<Int, Game>()
            val downloadInfoCache = HashMap<Int, Pair<Document, ItchWebsiteParser.DownloadUrl>?>()

            for (install in installations) {
                if (!updateChecker.shouldCheck(install))
                    continue

                launch(Dispatchers.IO) {
                    var result: UpdateCheckResult
                    val game: Game = if (gameCache.containsKey(install.gameId)) {
                        gameCache[install.gameId]!!
                    } else {
                        val dbGame = db.gameDao.getGameById(install.gameId)!!
                        gameCache[install.gameId] = dbGame
                        dbGame
                    }

                    try {
                        val downloadInfo = if (downloadInfoCache.containsKey(install.gameId)) {
                            downloadInfoCache[install.gameId]
                        } else {
                            val newDownloadInfo = updateChecker.getDownloadInfo(game)
                            downloadInfoCache[install.gameId] = newDownloadInfo
                            newDownloadInfo
                        }

                        if (downloadInfo == null) {
                            result = UpdateCheckResult(
                                install.internalId,
                                UpdateCheckResult.ACCESS_DENIED
                            )
                        } else {
                            val (updateCheckDoc, downloadUrlInfo) = downloadInfo
                            result = updateChecker.checkUpdates(
                                game, install, updateCheckDoc, downloadUrlInfo
                            )
                        }
                    } catch (e: Exception) {
                        result = UpdateCheckResult(
                            installationId = install.internalId,
                            code = UpdateCheckResult.ERROR,
                            errorReport = Utils.toString(e)
                        )
                        success = false
                        Log.e(LOGGING_TAG, "Update check error!", e)
                    }

                    launch(Dispatchers.IO) {
                        db.updateCheckDao.insert(result)
                    }
                    handleNotification(game, install, result)
                }
            }
        }

        if (success)
            Result.success()
        else
            Result.failure()
    }

    /**
     * @param result the result of the update check
     */
    private fun handleNotification(game: Game, install: Installation, result: UpdateCheckResult) {
        if(result.code == UpdateCheckResult.UP_TO_DATE)
            return

        val message = when (result.code) {
            UpdateCheckResult.UPDATE_NEEDED -> context.resources.getString(R.string.notification_update_available)
            UpdateCheckResult.EMPTY -> context.resources.getString(R.string.notification_update_empty)
            UpdateCheckResult.ACCESS_DENIED -> context.resources.getString(R.string.notification_update_access_denied)
//            UpdateCheckResult.UNKNOWN -> context.resources.getString(R.string.notification_update_unknown)
            else -> context.resources.getString(R.string.notification_update_fail)
        }
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                setSmallIcon(R.drawable.ic_mitch_notification)
                setContentTitle(game.name)
                setContentText(message)
                setAutoCancel(true)

                if (install.packageName != null) {
                    val icon = context.packageManager.getApplicationIcon(install.packageName)
                    setLargeIcon(Utils.drawableToBitmap(icon))
                }

                priority = NotificationCompat.PRIORITY_LOW

                val pendingIntent: PendingIntent

                if (result.code == UpdateCheckResult.UPDATE_NEEDED) {
                    if (result.uploadID != null) {
                        val intent = Intent(context, UpdateNotificationBroadcastReceiver::class.java).apply {
                            putExtra(UpdateNotificationBroadcastReceiver.EXTRA_INSTALL_ID, result.installationId)
                        }
                        pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    } else if (game.downloadPageUrl == null) {
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
            notify(NOTIFICATION_TAG_UPDATE_CHECK, game.gameId, builder.build())
        }
    }
}