package garden.appl.mitch.install

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.files.DownloadType


class InstallationDatabaseManager(val context: Context)  {
    companion object {
        private const val LOGGING_TAG = "InstallDatabaseHandler"
    }

    suspend fun onInstallResult(pendingInstall: Installation,
                                packageName: String?, status: Int
    ) {
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
                    packageName = packageName!!
                )
                Log.d(LOGGING_TAG, "New install: $newInstall")
                Installations.deleteOutdatedInstalls(context, pendingInstall)
                db.installDao.delete(pendingInstall)
                db.installDao.insert(newInstall)
            }
        }

        db.updateCheckDao.getUpdateCheckResultForUpload(pendingInstall.uploadId)?.let {
            it.isInstalling = false
            db.updateCheckDao.insert(it)
        }
    }

    suspend fun onDownloadComplete(pendingInstall: Installation, downloadType: DownloadType) {
        val db = AppDatabase.getDatabase(context)

        if (downloadType == DownloadType.INSTALL_MISC) {
            pendingInstall.status = Installation.STATUS_INSTALLED
            pendingInstall.downloadOrInstallId = null
            db.installDao.update(pendingInstall)
        } else {
            pendingInstall.status = Installation.STATUS_READY_TO_INSTALL
            db.installDao.update(pendingInstall)
        }
    }

    suspend fun onDownloadFailed(downloadId: Long) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadFailed")

        val pendingInstall =
            db.installDao.getPendingInstallationByDownloadId(downloadId) ?: return
        db.installDao.delete(pendingInstall)
    }

    suspend fun onInstallStart(downloadId: Long, pendingInstallId: Long) {
        Log.d(LOGGING_TAG, "onInstallStart")

        val db = AppDatabase.getDatabase(context)
        val pendingInstall = db.installDao.getPendingInstallationByDownloadId(downloadId)!!

        pendingInstall.status = Installation.STATUS_INSTALLING
        pendingInstall.downloadOrInstallId = pendingInstallId
        db.installDao.update(pendingInstall)
    }

    suspend fun onInstallStart(sessionId: Int) {
        onInstallStart(sessionId.toLong(), sessionId.toLong())
    }
}