package ua.gardenapple.itchupdater.gitlab

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.UpdateChecker
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.UpdateNotificationBroadcastReceiver
import ua.gardenapple.itchupdater.ui.MainActivity
import java.io.IOException

/**
 * Check if an update is available for the GitLab build and provide notification.
 */
class GitlabUpdateCheckWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val LOGGING_TAG = "SelfUpdaterWorker"

        val REPO_URL = Uri.parse("https://gitlab.com/gardenappl/mitch")
        const val VERSION_CHECK_URL = "https://gitlab.com/api/v4/projects/gardenappl%2Fmitch/releases"
        //Have to get download URL by parsing markdown from description, ugh
        //Strips leading slash
        val RELEASE_DESC_PATTERN = Regex("""\[\S+\]\(/?(\S+)\)""")
    }

    override suspend fun doWork(): Result = coroutineScope {
        val request = Request.Builder().run {
            url(VERSION_CHECK_URL)
            build()
        }

        try {
            val releasesData = JSONArray(withContext(Dispatchers.IO) {
                MitchApp.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful)
                        throw IOException("Unexpected response $response")
                    return@use response.body!!.string()
                }
            })

            val latestRelease = releasesData[0] as JSONObject
            val latestVersionString = latestRelease["name"] as String

            if (latestVersionString.contains(BuildConfig.VERSION_NAME)) {
                Log.d(LOGGING_TAG, "GitLab Mitch is up to date")
                handleNotification(UpdateCheckResult(UpdateCheckResult.UP_TO_DATE), null)
            } else {
                Log.d(LOGGING_TAG, "GitLab Mitch needs update")
                Log.d(LOGGING_TAG, "Current version: ${BuildConfig.VERSION_NAME}, GitLab says $latestVersionString")

                val descriptionMatch =
                    RELEASE_DESC_PATTERN.matchEntire(latestRelease["description"] as String)
                val relativeUrl = descriptionMatch!!.groupValues[1]
                Log.d(LOGGING_TAG, "Relative download URL is $relativeUrl")
                val absoluteUrl = Uri.withAppendedPath(REPO_URL, relativeUrl)
                Log.d(LOGGING_TAG, "Absolute download URL is $absoluteUrl")

                handleNotification(UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED), absoluteUrl)
            }

            return@coroutineScope Result.success()

        } catch (e: Exception) {
            Log.e(LOGGING_TAG, "Error occurred while checking updates for GitLab Mitch", e)
            handleNotification(null, null)
            return@coroutineScope Result.failure()
        }
    }

    /**
     * @param result the result of the update check, can be null if the update check failed (due to network error or parsing error)
     * @param downloadUrl URL for downloading the latest .apk, can be null if no new version detected
     */
    private fun handleNotification(result: UpdateCheckResult?, downloadUrl: Uri?) {
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
                setSmallIcon(R.drawable.ic_mitch_notification)
                setContentTitle(context.resources.getString(R.string.app_name))
                setContentText(message)
                setAutoCancel(true)

                val icon = context.packageManager.getApplicationIcon(context.applicationInfo)
                setLargeIcon(Utils.drawableToBitmap(icon))

                priority = NotificationCompat.PRIORITY_LOW

                val pendingIntent: PendingIntent

                if (result?.code == UpdateCheckResult.UPDATE_NEEDED) {
                    val intent = Intent(context, GitlabUpdateBroadcastReceiver::class.java)
                    intent.putExtra(GitlabUpdateBroadcastReceiver.EXTRA_DOWNLOAD_URL, downloadUrl.toString())

                    pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                } else {
                    val activityIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse(Game.MITCH_STORE_PAGE),
                        context,
                        MainActivity::class.java
                    )
                    pendingIntent = PendingIntent.getActivity(context, 0, activityIntent, 0)
                }

                setContentIntent(pendingIntent)
            }

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_SELF_UPDATE_CHECK, builder.build())
        }
    }
}