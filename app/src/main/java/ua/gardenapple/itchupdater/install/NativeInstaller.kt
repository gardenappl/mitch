package ua.gardenapple.itchupdater.install

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import ua.gardenapple.itchupdater.FILE_PROVIDER
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase
import java.io.File

class NativeInstaller : AbstractInstaller() {
    companion object {
        private const val LOGGING_TAG = "NativeInstaller"

        fun makeInstallId(uploadId: Int): Long {
            return uploadId.toLong() or (1L shl 63)
        }
    }

    override suspend fun requestInstall(context: Context, downloadId: Long, apkFile: File) {
        val db = AppDatabase.getDatabase(context)

        val pendingInstall = db.installDao.getPendingInstallationByDownloadId(downloadId)!!
        val installId = makeInstallId(pendingInstall.uploadId)
        Log.d(LOGGING_TAG, "Turning ${pendingInstall.uploadId} into $installId")
        Mitch.databaseHandler.onInstallStart(downloadId, installId)

        val intent = Intent(context, NativeInstallerActivity::class.java)
        intent.action = NativeInstallerActivity.ACTION_INSTALL_PACKAGE
        intent.data = apkFile.toUri()
        intent.putExtra(NativeInstallerActivity.EXTRA_INSTALL_ID, installId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    override suspend fun tryCancel(context: Context, installId: Long): Boolean {
        return false
    }

    override suspend fun isInstalling(context: Context, installId: Long): Boolean? {
        return null
    }
}