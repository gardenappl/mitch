package garden.appl.mitch.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.room.withTransaction
import garden.appl.mitch.ErrorReportBroadcastReceiver
import garden.appl.mitch.FILE_PROVIDER
import garden.appl.mitch.Mitch
import garden.appl.mitch.NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.files.DownloadFileListener
import garden.appl.mitch.files.DownloadType
import garden.appl.mitch.ui.MainActivity
import java.io.File

/**
 * Could be split into three DownloadFileListeners for the three different download types
 * that are handled, this is legacy code.
 */
class InstallationDownloadFileListener : DownloadFileListener() {
    companion object {
        const val LOGGING_TAG = "InstallDownListener"
    }

    private fun createResultNotification(
        context: Context,
        fileName: String,
        downloadType: DownloadType,
        downloadFile: File?,
        downloadOrInstallId: Long,
        errorName: String?,
        errorReport: String?
    ) {
        val pendingIntent: PendingIntent
        if (errorName != null) {
            val intent = Intent(context, ErrorReportBroadcastReceiver::class.java).apply {
                putExtra(ErrorReportBroadcastReceiver.EXTRA_ERROR_STRING, errorReport)
            }
            pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT, false)!!

        } else if (downloadType == DownloadType.INSTALL_SESSION) {
            val intent = Intent(context, InstallRequestBroadcastReceiver::class.java).apply {
                Log.d(LOGGING_TAG, "session ID: $downloadOrInstallId")
                putExtra(InstallRequestBroadcastReceiver.EXTRA_STREAM_SESSION_ID, downloadOrInstallId)
                putExtra(InstallRequestBroadcastReceiver.EXTRA_APK_NAME, fileName)
            }
            pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)!!

        } else if (downloadType == DownloadType.INSTALL_APK) {
            val intent = Intent(context, InstallRequestBroadcastReceiver::class.java).apply {
                data = Uri.fromFile(downloadFile)
                putExtra(InstallRequestBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadOrInstallId)
            }
            pendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)!!

        } else {
            val fileIntent = Utils.getIntentForFile(context, downloadFile!!, FILE_PROVIDER)

            val intent = if (fileIntent.resolveActivity(context.packageManager) == null) {
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOULD_OPEN_LIBRARY, true)
            } else {
                Intent.createChooser(fileIntent, context.resources.getString(R.string.select_app_for_file))
            }
            pendingIntent = PendingIntentCompat.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)!!
        }

        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALL_NEEDED).apply {
            setSmallIcon(R.drawable.ic_mitch_notification)
            if (errorName != null) {
                setContentTitle(context.resources.getString(R.string.notification_download_error, fileName))
                setContentText(errorName)
            } else {
                if (downloadType == DownloadType.INSTALL_MISC)
                    setContentTitle(context.resources.getString(R.string.notification_download_complete_title))
                else
                    setContentTitle(context.resources.getString(R.string.notification_install_title))
                setContentText(fileName)
            }

            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)

            notify(context, downloadOrInstallId, this.build())
        }
    }

    override suspend fun onCompleted(
        context: Context,
        fileName: String,
        uploadId: Int?,
        downloadOrInstallId: Long,
        type: DownloadType
    ) {
        val downloadFileManager = Mitch.installDownloadManager

        val db = AppDatabase.getDatabase(context)
        val pendingInstall = db.installDao.getPendingInstallationByDownloadId(downloadOrInstallId)!!

        val notificationFile: File? = when (type) {
            DownloadType.INSTALL_APK ->
                downloadFileManager.getPendingFile(uploadId!!)
            DownloadType.INSTALL_MISC -> {
                Installations.deleteOutdatedInstalls(context, pendingInstall)
                downloadFileManager.replacePendingFile(uploadId!!)

                downloadFileManager.getDownloadedFile(uploadId)
            }
            else -> null
        }
        createResultNotification(context, fileName, type, notificationFile, downloadOrInstallId, null, null)
        Mitch.databaseHandler.onDownloadComplete(pendingInstall, type)
    }

    override suspend fun onError(
        context: Context,
        fileName: String,
        uploadId: Int?,
        downloadOrInstallId: Long,
        type: DownloadType,
        errorName: String,
        throwable: Throwable?
    ) {
        createResultNotification(context, fileName, type, null, downloadOrInstallId, errorName, Utils.toString(throwable))

        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            Mitch.installDownloadManager.deletePendingFile(uploadId!!)
            Mitch.databaseHandler.onDownloadFailed(downloadOrInstallId)
        }
    }
}
