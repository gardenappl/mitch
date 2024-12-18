package garden.appl.mitch.client

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import garden.appl.mitch.ErrorReportBroadcastReceiver
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_UPDATES
import garden.appl.mitch.NOTIFICATION_TAG_UPDATE_CHECK
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import java.net.SocketTimeoutException

class UpdateChecker(private val context: Context) {
    companion object {
        private const val LOGGING_TAG = "UpdateCheckWorker"
    }

    suspend fun checkUpdates(): Result {
        // Avoid a bunch of individual errors, if we just have no network connection
        if (!Utils.isNetworkConnected(context)) {
            Log.w(LOGGING_TAG, "No network connection detected, aborting")
            return Result.failure()
        }

        val db = AppDatabase.getDatabase(context)
        val updateChecker = SingleUpdateChecker(db)
        val installations = db.installDao.getFinishedInstallationsAndSubscriptionsSync()
            .filter { install -> updateChecker.shouldCheck(install) }
        for (install in installations)
            Log.d(LOGGING_TAG, "Will check for $install")

        var success = true

        coroutineScope {
            //We support multiple installs per game, and we don't want to download the
            //HTML for the same game multiple times
            val gameCache = HashMap<Int, Game>()
            val downloadInfoCache = HashMap<Int, Pair<Document, ItchWebsiteParser.DownloadUrl>?>()

            for (install in installations) {
                if (!isActive)
                    return@coroutineScope Result.failure()
                delay(1000)

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
                            Log.d(LOGGING_TAG,
                                "Download URL for ${game.name}: ${newDownloadInfo?.second}")

                            newDownloadInfo
                        }

                        if (downloadInfo == null) {
                            Log.d(LOGGING_TAG, "null download info for $game")
                            result = UpdateCheckResult(install.internalId,
                                UpdateCheckResult.ACCESS_DENIED)
                        } else {
                            val (updateCheckDoc, downloadUrlInfo) = downloadInfo
                            result = updateChecker.checkUpdates(game, install,
                                updateCheckDoc, downloadUrlInfo)
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
                        Log.d(LOGGING_TAG, "Inserting $result")
                        db.updateCheckDao.insert(result)
                    }
                    handleNotification(game, install, result)
                }
            }
        }

//        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
//        sharedPrefs.edit().run {
//            this.putLong(PREF_LAST_UPDATE_CHECK, Instant.now().toEpochMilli())
//            commit()
//        }

        return if (success)
            Result.success()
        else
            Result.failure()
    }

    /**
     * @param result the result of the update check
     */
    private fun handleNotification(game: Game, install: Installation, result: UpdateCheckResult) {
        if (result.code == UpdateCheckResult.UP_TO_DATE)
            return

        val message = when (result.code) {
            UpdateCheckResult.UPDATE_AVAILABLE -> context.resources.getString(R.string.notification_update_available)
            UpdateCheckResult.EMPTY -> context.resources.getString(R.string.notification_update_empty)
            UpdateCheckResult.ACCESS_DENIED -> context.resources.getString(R.string.notification_update_access_denied)
//            UpdateCheckResult.UNKNOWN -> context.resources.getString(R.string.notification_update_unknown)
            else -> context.resources.getString(R.string.notification_update_fail)
        }
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_UPDATES).apply {
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
                    pendingIntent = PendingIntentCompat.getBroadcast(context, 0,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT, false)!!
                } else if (game.downloadPageUrl == null) {
                    val activityIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(game.storeUrl).run {
                            val builder = this.buildUpon()
                            builder.appendPath("purchase")
                            builder.build() },
                        context,
                        MainActivity::class.java
                    )
                    pendingIntent = PendingIntentCompat.getActivity(context, 0, activityIntent,
                            0, false)!!
                } else {
                    val activityIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(game.downloadPageUrl),
                        context,
                        MainActivity::class.java
                    )
                    pendingIntent = PendingIntentCompat.getActivity(context, 0, activityIntent,
                            0, false)!!
                }
            } else if (result.code == UpdateCheckResult.ERROR) {
                val intent = Intent(context, ErrorReportBroadcastReceiver::class.java).apply {
                    putExtra(ErrorReportBroadcastReceiver.EXTRA_ERROR_STRING, result.errorReport)
                }
                pendingIntent = PendingIntentCompat.getBroadcast(context, result.installationId,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT, false)!!
            } else {
                val activityIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(game.storeUrl),
                    context,
                    MainActivity::class.java
                )
                pendingIntent = PendingIntentCompat.getActivity(context, 0, activityIntent,
                        0, false)!!
            }
            setContentIntent(pendingIntent)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_TAG_UPDATE_CHECK, install.internalId, builder.build())
            }
        }
    }

    class Worker(appContext: Context, params: WorkerParameters)
        : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            return UpdateChecker(applicationContext).checkUpdates()
        }
    }
}