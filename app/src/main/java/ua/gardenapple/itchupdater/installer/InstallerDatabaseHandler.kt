package ua.gardenapple.itchupdater.installer

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
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

        val install = db.installDao.findPendingInstallationBySessionId(installSessionId) ?: return

        when(status) {
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE ->
            {
                db.installDao.delete(install)
            }
            PackageInstaller.STATUS_SUCCESS ->
            {
                val newInstall = install.copy(
                    status = Installation.STATUS_INSTALLED,
                    downloadOrInstallId = null,
                    packageName = packageName
                )
                Log.d(LOGGING_TAG, "New install: $newInstall")
                db.installDao.resetAllInstallationsForGame(install.gameId, newInstall)

                val pendingUploads = db.uploadDao.getPendingUploadsForGame(install.gameId)
                for (upload in pendingUploads) {
                    upload.isPending = false
                }
                db.uploadDao.resetAllUploadsForGame(install.gameId, pendingUploads)
            }
        }
    }

    override suspend fun onDownloadComplete(downloadId: Long, isInstallable: Boolean) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadComplete")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return
        if (pendingInstall.status != Installation.STATUS_DOWNLOADING)
            return

        if (isInstallable) {
            pendingInstall.status = Installation.STATUS_READY_TO_INSTALL
            db.installDao.update(pendingInstall)
        } else {
            val pendingUploads = db.uploadDao.getPendingUploadsForGame(pendingInstall.gameId)
            for (upload in pendingUploads) {
                upload.isPending = false
            }
            db.uploadDao.resetAllUploadsForGame(pendingInstall.gameId, pendingUploads)

            pendingInstall.downloadOrInstallId = null
            pendingInstall.status = Installation.STATUS_INSTALLED
            db.installDao.resetAllInstallationsForGame(pendingInstall.gameId, pendingInstall)
        }
    }

    override suspend fun onDownloadFailed(downloadId: Long) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadFailed")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return
        db.installDao.delete(pendingInstall)
    }

    override suspend fun onInstallStart(downloadId: Long, pendingInstallSessionId: Int) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onInstallStart")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId) ?: return

        pendingInstall.status = Installation.STATUS_INSTALLING
        pendingInstall.downloadOrInstallId = pendingInstallSessionId.toLong()
        db.installDao.update(pendingInstall)
    }
}