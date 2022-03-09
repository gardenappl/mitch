package ua.gardenapple.itchupdater.install

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.database.AppDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedOutputStream

class NativeInstaller : AbstractInstaller() {
    companion object {
        private const val LOGGING_TAG = "NativeInstaller"

        fun makeInstallId(uploadId: Int): Long {
            return uploadId.toLong() or (1L shl 63)
        }
    }

    override val type = Type.File

    override fun createSessionForStreamInstall(context: Context): Int {
        throw NotImplementedError()
    }

    override suspend fun openWriteStream(context: Context, sessionId: Int, lengthBytes: Long): OutputStream {
        TODO("Not yet implemented")
    }

    override suspend fun finishStreamInstall(context: Context, sessionId: Int, appName: String) {
        throw NotImplementedError()
    }

    override suspend fun requestInstall(context: Context, downloadId: Long, apkFile: File) {
        val db = AppDatabase.getDatabase(context)

        val pendingInstall = db.installDao.getPendingInstallationByDownloadId(downloadId)!!
        val installId = makeInstallId(pendingInstall.uploadId)
        Log.d(LOGGING_TAG, "Turning ${pendingInstall.uploadId} into $installId")
        Mitch.databaseHandler.onInstallStart(downloadId, installId)

        val intent = Intent(context, NativeInstallerActivity::class.java).apply {
            action = NativeInstallerActivity.ACTION_INSTALL_PACKAGE
            data = apkFile.toUri()
            putExtra(NativeInstallerActivity.EXTRA_INSTALL_ID, installId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override suspend fun tryCancel(context: Context, installId: Long): Boolean {
        return false
    }

    override suspend fun isInstalling(context: Context, installId: Long): Boolean? {
        return null
    }
}