package garden.appl.mitch.client

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.util.LinkedList

class UpdateChecker(private val context: Context) {
    companion object {
        private const val LOGGING_TAG = "UpdateCheckWorker"

        private const val DELAY_MILLIS: Long = 2000
        private const val ATTEMPTS = 3
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

        // We support multiple installs per game, and we don't want to download the
        // HTML for the same game multiple times
        val gameIds = installations.map { it.gameId }.toImmutableSet().toImmutableList()
        val gameAttemptsQueue = LinkedList(db.gameDao.getGamesByIdsSync(gameIds)
            .map { game -> Pair(0, game) })

        var first = true
        var success = true

        while (gameAttemptsQueue.isNotEmpty()) {
            if (!first) {
                delay(DELAY_MILLIS)
            } else {
                first = false
            }
            val (attempts, game) = gameAttemptsQueue.removeFirst()
            Log.d(LOGGING_TAG, "next in queue: ${game.name} (attempts: $attempts)")
            val installsForGame = installations.filter { it.gameId == game.gameId }

            val downloadInfo: SingleUpdateChecker.DownloadInfo
            try {
                downloadInfo = updateChecker.getDownloadInfo(game)
            } catch (_: CancellationException) {
                success = false
                break
            } catch (_: SocketTimeoutException) {
                success = false
                break
            } catch (e: Exception) {
                if (attempts < ATTEMPTS) {
                    gameAttemptsQueue.addLast(Pair(attempts + 1, game))
                } else {
                    for (install in installsForGame) {
                        val result = UpdateCheckResult(
                            installationId = install.internalId,
                            code = UpdateCheckResult.ERROR,
                            errorReport = Utils.toString(e)
                        )
                        handleResult(game, install, result)
                    }
                    success = false
                    Log.e(LOGGING_TAG, "Update check error!", e)
                }
                continue
            }

            for (install in installsForGame) {
                val result = if (downloadInfo.accessDenied) {
                    Log.d(LOGGING_TAG, "access denied for $game")
                    UpdateCheckResult(
                        install.internalId,
                        UpdateCheckResult.ACCESS_DENIED
                    )
                } else {
                    val (updateCheckDoc, downloadUrlInfo) = downloadInfo
                    updateChecker.checkUpdates(
                        game, install,
                        updateCheckDoc!!, downloadUrlInfo!!
                    )
                }
                handleResult(game, install, result)
            }
        }

        return if (success)
            Result.success()
        else
            Result.failure()
    }

    /**
     * @param result the result of the update check
     */
    private suspend fun handleResult(
        game: Game,
        install: Installation,
        result: UpdateCheckResult
    ) {
        withContext(Dispatchers.IO) {
            Log.d(LOGGING_TAG, "Inserting $result")
            val db = AppDatabase.getDatabase(context)
            db.updateCheckDao.insert(result)
        }

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
                        game.storeUrl.toUri().run {
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
                        game.downloadPageUrl.toUri(),
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
                    game.storeUrl.toUri(),
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