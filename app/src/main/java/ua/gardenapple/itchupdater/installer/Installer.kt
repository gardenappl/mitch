package ua.gardenapple.itchupdater.installer

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.MitchApp
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

    suspend fun install(context: Context, downloadId: Int, uploadId: Int) = withContext(Dispatchers.IO) {
        val sessionID = createSession(context)
        Log.d(LOGGING_TAG, "Created session")

        InstallerEvents.notifyApkInstallStart(downloadId, sessionID)
        Log.d(LOGGING_TAG, "Notified")

        val dir = File(MitchApp.downloadFileManager.pendingPath, uploadId.toString())
        install(Uri.parse(dir.listFiles()!![0].path), sessionID, context)
        Log.d(LOGGING_TAG, "Installed")
    }

    suspend fun install(context: Context, downloadId: Int, apkUri: Uri) = withContext(Dispatchers.IO) {
        val sessionID = createSession(context)
        Log.d(LOGGING_TAG, "Created session")

        InstallerEvents.notifyApkInstallStart(downloadId, sessionID)
        Log.d(LOGGING_TAG, "Notified")

        install(apkUri, sessionID, context)
        Log.d(LOGGING_TAG, "Installed")
    }

    private suspend fun install(apkUri: Uri, sessionId: Int, context: Context) = withContext(Dispatchers.IO) {
        val pkgInstaller = context.packageManager.packageInstaller

        val session = pkgInstaller.openSession(sessionId)

        val apkFile = File(apkUri.path!!)

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
            .putExtra(InstallerService.EXTRA_APK_NAME, apkUri.lastPathSegment)
        val pendingIntent = PendingIntent.getService(
            context, sessionId, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}