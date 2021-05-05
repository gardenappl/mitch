package ua.gardenapple.itchupdater.installer

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation

class Installations {
    companion object {
        private const val LOGGING_TAG = "Installations"
        
        suspend fun deleteFinishedInstall(context: Context, uploadId: Int) =
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                db.installDao.deleteFinishedInstallation(uploadId)
                Mitch.fileManager.deleteDownloadedFile(uploadId)
            }

        suspend fun deleteOutdatedInstalls(context: Context, pendingInstall: Installation) {
            deleteFinishedInstall(context, pendingInstall.uploadId)

            if (pendingInstall.availableUploadIds == null)
                return

            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val finishedInstalls =
                    db.installDao.getFinishedInstallationsForGame(pendingInstall.gameId)

                for (finishedInstall in finishedInstalls) {
                    if (!pendingInstall.availableUploadIds.contains(finishedInstall.uploadId))
                        deleteFinishedInstall(context, finishedInstall.uploadId)
                }
            }
        }
        
        suspend fun cancelPending(context: Context, pendingInstall: Installation) =
            cancelPending(
                context,
                pendingInstall.status,
                pendingInstall.downloadOrInstallId!!,
                pendingInstall.uploadId,
                pendingInstall.internalId
            )
        
        suspend fun cancelPending(
            context: Context,
            status: Int,
            downloadOrInstallId: Int,
            uploadId: Int,
            installId: Int
        ) {
            if (status == Installation.STATUS_INSTALLED)
                throw IllegalArgumentException("Tried to cancel installed Installation")

            if (status == Installation.STATUS_INSTALLING) {
                val pkgInstaller = context.packageManager.packageInstaller
                try {
                    pkgInstaller.abandonSession(downloadOrInstallId)
                } catch (e: SecurityException) {
                    Log.e(LOGGING_TAG, "Could not cancel", e)
                }
            } else {
                val notificationService =
                    context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
                notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD, downloadOrInstallId)
            }

            withContext(Dispatchers.IO) {
                if (status == Installation.STATUS_DOWNLOADING) {
                    Log.d(LOGGING_TAG, "Cancelling $downloadOrInstallId")
                    Mitch.fileManager.requestCancel(downloadOrInstallId, uploadId)
                } else {
                    Mitch.fileManager.deletePendingFile(uploadId)
                }
                val db = AppDatabase.getDatabase(context)
                db.installDao.delete(installId)

                db.updateCheckDao.getUpdateCheckResultForUpload(uploadId)?.let {
                    it.isInstalling = false
                    db.updateCheckDao.insert(it)
                }
            }
        }
    }
}