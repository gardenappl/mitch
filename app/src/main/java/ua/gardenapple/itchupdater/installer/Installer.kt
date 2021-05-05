package ua.gardenapple.itchupdater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.Utils
import java.io.File

class Installer {
    companion object {
        private const val LOGGING_TAG = "Installer"
    }

    /**
     * Returns the PackageInstaller session ID.
     */
    private fun createSession(context: Context): Int {
        Log.d(LOGGING_TAG, "Creating session...")
        val pkgInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        return pkgInstaller.createSession(params)
    }

    suspend fun install(context: Context, downloadId: Long, uploadId: Int) = withContext(Dispatchers.IO) {
        install(context, downloadId, Mitch.fileManager.getPendingFile(uploadId)!!)
    }

    suspend fun install(context: Context, downloadId: Long, apkFile: File) = withContext(Dispatchers.IO) {
        val sessionID = createSession(context)
        Log.d(LOGGING_TAG, "Created session")

        Mitch.databaseHandler.onInstallStart(downloadId, sessionID)
        Log.d(LOGGING_TAG, "Notified")

        doInstall(apkFile, sessionID, context)
        Log.d(LOGGING_TAG, "Installed")
    }

    private suspend fun doInstall(apkFile: File, sessionId: Int, context: Context) = withContext(Dispatchers.IO) {
        val pkgInstaller = context.packageManager.packageInstaller

        val session = pkgInstaller.openSession(sessionId)

        try {
            apkFile.inputStream().use { inputStream ->
                session.openWrite(apkFile.name, 0, apkFile.length()).use { outputStream ->
                    Utils.copy(inputStream, outputStream)
                    session.fsync(outputStream)
                }
            }
        } catch (e: Exception) {
            session.abandon()
            Log.e(LOGGING_TAG, "Error occurred while creating install session", e)
        }

        val callbackIntent = Intent(context, InstallerService::class.java)
            .putExtra(InstallerService.EXTRA_APK_NAME, apkFile.name)
        val pendingIntent = PendingIntent.getService(
            context, sessionId, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}