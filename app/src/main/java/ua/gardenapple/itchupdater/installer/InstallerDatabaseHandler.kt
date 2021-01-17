package ua.gardenapple.itchupdater.installer

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation


class InstallerDatabaseHandler(val context: Context)  {
    companion object {
        private const val LOGGING_TAG = "InstallDatabaseHandler"
    }

    suspend fun onInstallResult(pendingInstall: Installation, packageName: String, status: Int) =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            Log.d(LOGGING_TAG, "onInstallComplete")

            when (status) {
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.STATUS_FAILURE_ABORTED,
                PackageInstaller.STATUS_FAILURE_BLOCKED,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_STORAGE -> {
                    db.installDao.delete(pendingInstall)
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    val newInstall = pendingInstall.copy(
                        status = Installation.STATUS_INSTALLED,
                        downloadOrInstallId = null,
                        packageName = packageName
                    )
                    Log.d(LOGGING_TAG, "New install: $newInstall")
                    db.installDao.deleteFinishedInstallation(packageName)
                    db.installDao.delete(pendingInstall)
                    db.installDao.insert(newInstall)
                }
            }
        }

    //TODO: delete old uploadId(s)
    suspend fun onDownloadComplete(downloadId: Int, isInstallable: Boolean) =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            Log.d(LOGGING_TAG, "onDownloadComplete")

            val pendingInstall =
                db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return@withContext

            if (isInstallable) {
                pendingInstall.status = Installation.STATUS_READY_TO_INSTALL
                db.installDao.update(pendingInstall)
            } else {
                db.installDao.deleteFinishedInstallation(pendingInstall.uploadId)
                pendingInstall.status = Installation.STATUS_INSTALLED
                pendingInstall.downloadOrInstallId = null
                db.installDao.update(pendingInstall)
            }
        }

    suspend fun onDownloadFailed(downloadId: Int) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadFailed")

        val pendingInstall =
            db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return@withContext
        db.installDao.delete(pendingInstall)
    }

    suspend fun onInstallStart(downloadId: Int, pendingInstallSessionId: Int) =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            Log.d(LOGGING_TAG, "onInstallStart")

            val pendingInstall =
                db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return@withContext

            pendingInstall.status = Installation.STATUS_INSTALLING
            pendingInstall.downloadOrInstallId = pendingInstallSessionId
            db.installDao.update(pendingInstall)
        }
}