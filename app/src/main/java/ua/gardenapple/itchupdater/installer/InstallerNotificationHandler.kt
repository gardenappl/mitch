package ua.gardenapple.itchupdater.installer

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.NOTIFICATION_CHANNEL_ID_INSTALLING
import ua.gardenapple.itchupdater.NOTIFICATION_ID_INSTALLING
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game

class InstallerNotificationHandler(val context: Context) : InstallCompleteListener {
    override suspend fun onInstallComplete(installSessionId: Int, apkName: String, game: Game, status: Int) {
        //TODO notification on failure
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                setSmallIcon(R.drawable.ic_file_download_black_24dp)
                setContentTitle(context.resources.getString(R.string.notification_install_complete_title))
                setContentText(game.name)
                priority = NotificationCompat.PRIORITY_HIGH
                //TODO intent for "install complete" notification
            }

        with(NotificationManagerCompat.from(context)) {
            //TODO: better system for notification IDs
            notify(NOTIFICATION_ID_INSTALLING + installSessionId, builder.build())
        }
    }

}