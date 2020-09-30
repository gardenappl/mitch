package ua.gardenapple.itchupdater.installer

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase

/**
 * This receiver responds to finished file downloads from DownloadManager.
 */
class FileDownloadBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val query = DownloadManager.Query()
        query.setFilterById(downloadID)

        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val downloadLocalUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            val downloadLocalUri = Uri.parse(downloadLocalUriString)
            val downloadMimeType = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE))

            if (downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                val isApk = downloadMimeType == DownloadRequester.APK_MIME ||
                        downloadLocalUri!!.path!!.endsWith(".apk")
                createNotification(context, downloadLocalUri, downloadID, isApk)

                if(!isApk)
                    InstallerEvents.notifyDownloadComplete(downloadID, null)
            }
            Log.d(LOGGING_TAG, downloadMimeType)
        }
        cursor.close()
    }


    private fun createNotification(context: Context, downloadLocalUri: Uri, id: Long, isApk: Boolean) {
        val pendingIntent: PendingIntent
        if(isApk) {
            val downloadPath = downloadLocalUri.path!!
            Log.d(LOGGING_TAG, downloadPath)

//            val providerUri = FileProvider.getUriForFile(context,
//                "ua.gardenapple.itchupdater.fileprovider", File(downloadPath))
//            Log.d(LOGGING_TAG, providerUri.toString())

            val intent = Intent(context, InstallNotificationBroadcastReceiver::class.java).apply {
                data = Uri.parse(downloadPath)
                putExtra(InstallNotificationBroadcastReceiver.EXTRA_DOWNLOAD_ID, id)
            }
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        } else {
//            var uri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toURI()
//            val dirUri = FileProvider.getUriForFile(context, "ua.gardenapple.itchupdater.fileprovider",
//                    File(uri).resolve("itchAnd"))
//
//            Log.d(LOGGING_TAG, dirUri.toString())
//            intent = Intent(Intent.ACTION_VIEW).apply {
//                setDataAndType(dirUri, "resource/folder")
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            }
//
//            //if there's no file manager installed
//            if(intent.resolveActivityInfo(context.packageManager, 0) == null) {
//                Log.d(LOGGING_TAG, "no file manager?")
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
//            }
        }

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            if(isApk)
                setContentTitle(context.resources.getString(R.string.notification_install_title))
            else
                setContentTitle(context.resources.getString(R.string.notification_download_complete_title))

            setContentText(downloadLocalUri.lastPathSegment)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_DOWNLOAD + (id % 100000).toInt(), builder.build())
        }
    }
}