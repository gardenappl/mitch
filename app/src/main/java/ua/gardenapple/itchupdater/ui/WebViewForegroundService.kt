package ua.gardenapple.itchupdater.ui

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ua.gardenapple.itchupdater.NOTIFICATION_CHANNEL_ID_WEB_RUNNING
import ua.gardenapple.itchupdater.R

class WebViewForegroundService : Service() {
    companion object {
        private const val LOGGING_TAG = "WebViewForeground"
        
        const val EXTRA_ORIGINAL_INTENT = "original_intent"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOGGING_TAG, "Foreground service started")

        val pendingIntent = intent?.getParcelableExtra<Intent?>(EXTRA_ORIGINAL_INTENT)?.let {
                originalIntent -> PendingIntent.getActivity(this, 0, originalIntent, 0)
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_WEB_RUNNING).run {
            setContentTitle(resources.getString(R.string.notification_game_running))
            setSmallIcon(R.drawable.ic_mitch_notification)

            priority = NotificationCompat.PRIORITY_LOW

            pendingIntent?.apply {
                setContentIntent(this)
            }

            build()
        }

        startForeground(1, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOGGING_TAG, "foreground service destroyed")
    }
}