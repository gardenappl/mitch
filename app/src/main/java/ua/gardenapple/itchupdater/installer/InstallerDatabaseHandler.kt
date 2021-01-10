package ua.gardenapple.itchupdater.installer

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation


class InstallerDatabaseHandler(val context: Context) :
    InstallResultListener, DownloadCompleteListener, InstallStartListener, DownloadFailListener {
    companion object {
        private const val LOGGING_TAG = "InstallDatabaseHandler"
    }

    override suspend fun onInstallResult(installSessionId: Int, packageName: String, apkName: String?, status: Int) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onInstallComplete")

        val pendingInstall = db.installDao.findPendingInstallationBySessionId(installSessionId) ?: return
        MitchApp.downloadFileManager.deletePendingFiles(pendingInstall.uploadId)

        when(status) {
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE ->
            {
                db.installDao.delete(pendingInstall)
            }
            PackageInstaller.STATUS_SUCCESS ->
            {
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

    override suspend fun onDownloadComplete(downloadId: Int, isInstallable: Boolean) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadComplete")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return

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

    override suspend fun onDownloadFailed(downloadId: Int) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadFailed")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return
        db.installDao.delete(pendingInstall)
    }

    override suspend fun onInstallStart(downloadId: Int, pendingInstallSessionId: Int) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onInstallStart")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return

        pendingInstall.status = Installation.STATUS_INSTALLING
        pendingInstall.downloadOrInstallId = pendingInstallSessionId
        db.installDao.update(pendingInstall)
    }
}