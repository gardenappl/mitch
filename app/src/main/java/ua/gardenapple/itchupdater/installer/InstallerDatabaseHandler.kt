package ua.gardenapple.itchupdater.installer

import android.content.Context
import android.util.Log
import ua.gardenapple.itchupdater.database.AppDatabase


class InstallerDatabaseHandler(val context: Context) : InstallCompleteListener, DownloadCompleteListener {
    companion object {
        const val LOGGING_TAG = "InstallDatabaseHandler"
    }

    override suspend fun onInstallComplete(apkName: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun onDownloadComplete(downloadId: Long, installPending: Boolean) {
        val db = AppDatabase.getDatabase(context)
        Log.d(LOGGING_TAG, "onDownloadComplete")

        val pendingInstall = db.installDao.findPendingInstallationByDownloadId(downloadId)!!
        val pendingUploads = db.uploadDao.getPendingUploadsForGame(pendingInstall.gameId)
        for(upload in pendingUploads) {
            upload.isPending = false
        }
        db.uploadDao.clearAllUploadsForGame(pendingInstall.gameId)
        db.uploadDao.insert(pendingUploads)

        pendingInstall.downloadId = null
        pendingInstall.isPending = false
        db.installDao.clearAllInstallationsForGame(pendingInstall.gameId)
        db.installDao.insert(pendingInstall)
    }
}