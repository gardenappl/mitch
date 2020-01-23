package ua.gardenapple.itchupdater.installer

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation


class InstallerDatabaseHandler(val context: Context) : InstallCompleteListener, DownloadCompleteListener {
    companion object {
        const val LOGGING_TAG = "InstallDatabaseHandler"
    }

    override suspend fun onInstallComplete(installSessionId: Int, apkName: String, game: Game, status: Int) {
        val db = AppDatabase.getDatabase(context)

        when(status) {
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE ->
            {
                val install = db.installDao.findPendingInstallationBySessionId(installSessionId)
                if(install != null)
                    db.installDao.delete(install)
            }
            PackageInstaller.STATUS_SUCCESS ->
            {
                val install = db.installDao.findPendingInstallationBySessionId(installSessionId)!!
                install.status = Installation.STATUS_INSTALLED
                install.downloadOrInstallId = null
                db.installDao.update(install)
            }
        }
    }

    override suspend fun onDownloadComplete(downloadId: Long, pendingInstallId: Int?) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadComplete")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId)!!

        if(pendingInstallId == null) {
            val pendingUploads = db.uploadDao.getPendingUploadsForGame(pendingInstall.gameId)
            for (upload in pendingUploads) {
                upload.isPending = false
            }
            db.uploadDao.clearAllUploadsForGame(pendingInstall.gameId)
            db.uploadDao.insert(pendingUploads)

            pendingInstall.downloadOrInstallId = null
            pendingInstall.status = Installation.STATUS_INSTALLED
            db.installDao.clearAllInstallationsForGame(pendingInstall.gameId)
            db.installDao.insert(pendingInstall)

        } else {
            pendingInstall.status = Installation.STATUS_INSTALLING
            pendingInstall.downloadOrInstallId = pendingInstallId.toLong()
            db.installDao.update(pendingInstall)
        }
    }
}