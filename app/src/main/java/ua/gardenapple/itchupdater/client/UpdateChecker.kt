package ua.gardenapple.itchupdater.client

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import kotlinx.coroutines.*
import org.acra.ACRA
import org.json.JSONException
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.UpdateNotificationBroadcastReceiver
import ua.gardenapple.itchupdater.ui.MainActivity
import java.lang.RuntimeException
import java.net.SocketTimeoutException

class UpdateChecker(private val context: Context) {
    companion object {
        private const val LOGGING_TAG = "UpdateCheckWorker"
    }

    suspend fun checkUpdates(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val installations = db.installDao.getFinishedInstallationsSync()
        val updateChecker = SingleUpdateChecker(db)
        var success = true

        coroutineScope {
            //We support multiple install per game, and we don't want to download the
            //HTML for the same game multiple times
            val gameCache = HashMap<Int, Game>()
            val downloadInfoCache = HashMap<Int, Pair<Document, ItchWebsiteParser.DownloadUrl>?>()

            for (install in installations) {
                if (!isActive)
                    return@coroutineScope Result.failure()
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
                            Log.d(LOGGING_TAG, "Download URL for ${game.name}: ${newDownloadInfo?.second}")
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
                    } catch (e: CancellationException) {
                        return@launch
                    } catch (e: SocketTimeoutException) {
                        return@launch
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

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPrefs.edit().run {
//            this.putLong(PREF_LAST_UPDATE_CHECK, Instant.now().toEpochMilli())
            commit()
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
            UpdateCheckResult.UPDATE_AVAILABLE -> context.resources.getString(R.string.notification_update_available)
            UpdateCheckResult.EMPTY -> context.resources.getString(R.string.notification_update_empty)
            UpdateCheckResult.ACCESS_DENIED -> context.resources.getString(R.string.notification_update_access_denied)
//            UpdateCheckResult.UNKNOWN -> context.resources.getString(R.string.notification_update_unknown)
            else -> context.resources.getString(R.string.notification_update_fail)
        }
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_UPDATES).apply {
                setSmallIcon(R.drawable.ic_mitch_notification)
                setContentText(message)
                setAutoCancel(true)

                if (install.packageName != null) {
                    try {
                        val info = context.packageManager.getApplicationInfo(install.packageName, 0)
                        val icon = context.packageManager.getApplicationIcon(info)
                        setContentTitle(context.packageManager.getApplicationLabel(info))
                        setLargeIcon(Utils.drawableToBitmap(icon))
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(LOGGING_TAG, "Error: application ${install.packageName} not found!", e)
                        setContentTitle(install.uploadName)
                    }
                } else {
                    setContentTitle(install.uploadName)
                }

                priority = NotificationCompat.PRIORITY_LOW

                val pendingIntent: PendingIntent
                if (result.code == UpdateCheckResult.UPDATE_AVAILABLE) {
                    if (result.uploadID != null) {
                        val intent = Intent(context, UpdateNotificationBroadcastReceiver::class.java).apply {
                            putExtra(UpdateNotificationBroadcastReceiver.EXTRA_INSTALL_ID, result.installationId)
                        }
                        pendingIntent = PendingIntent.getBroadcast(context, 0,
                            intent, PendingIntent.FLAG_UPDATE_CURRENT)
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
                } else if (result.code == UpdateCheckResult.ERROR) {
                    val intent = Intent(context, ErrorReportBroadcastReciever::class.java).apply {
                        putExtra(ErrorReportBroadcastReciever.EXTRA_ERROR_STRING, result.errorReport)
                    }
                    pendingIntent = PendingIntent.getBroadcast(context, result.installationId,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT)
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
            notify(NOTIFICATION_TAG_UPDATE_CHECK, install.internalId, builder.build())
        }
    }


    class Worker(appContext: Context, params: WorkerParameters)
        : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            return UpdateChecker(applicationContext).checkUpdates()
        }
    }
}