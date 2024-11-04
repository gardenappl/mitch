package garden.appl.mitch.ui

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_WEB_RUNNING
import garden.appl.mitch.R


/**
 * This is just a dummy foreground service, which is started while the [GameActivity] is running.
 * Hopefully this will prevent Android form killing the app while
 * an HTML5 game is running in the background.
 */
class GameForegroundService : Service() {

    companion object {
        private const val LOGGING_TAG = "WebViewForeground"

        const val EXTRA_ORIGINAL_INTENT = "original_intent"
        const val EXTRA_GAME_ID = "game_id"

        const val TRANSACT_TYPE_GAME_ID = 69
    }

    private var gameId: Int = -1

    inner class Binder : android.os.Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (data.readInt() == TRANSACT_TYPE_GAME_ID) {
                reply?.writeInt(gameId)
                return true
            }
            return false
        }
    }

    override fun onBind(p0: Intent?): IBinder? = this.Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOGGING_TAG, "Foreground service started")

        val pendingIntent =
            intent?.getParcelableExtra<Intent?>(EXTRA_ORIGINAL_INTENT)?.let { originalIntent ->
                PendingIntentCompat.getActivity(this, 0, originalIntent, 0, false)
            }
        gameId = intent?.getIntExtra(EXTRA_GAME_ID, -1) ?: -1

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