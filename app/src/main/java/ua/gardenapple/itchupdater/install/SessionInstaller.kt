package ua.gardenapple.itchupdater.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase
import java.io.File
import java.io.IOException

//TODO: major overfaul of SessionInstaller (allow streaming directly from download URL)
class SessionInstaller : AbstractInstaller() {
    class NotEnoughSpaceException(e: IOException) : IOException(e)

    companion object {
        private const val LOGGING_TAG = "SessionInstaller"
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

    private suspend fun doInstall(
        apkFile: File,
        downloadId: Int,
        sessionId: Int,
        context: Context
    ) = withContext(Dispatchers.IO) {
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
            if (e is IOException && e.message?.startsWith("Failed to allocate") == true) {
                Log.w(LOGGING_TAG, "Looks like not enough space", e)
                throw NotEnoughSpaceException(e)
            } else if (e is CancellationException) {
                Log.w(LOGGING_TAG, "Cancelled")
                throw e
            } else {
                throw e
            }
        }

        val callbackIntent = Intent(context, SessionInstallerService::class.java)
            .putExtra(SessionInstallerService.EXTRA_APK_PATH, apkFile.path)
            .putExtra(SessionInstallerService.EXTRA_DOWNLOAD_ID, downloadId)
        val pendingIntent = PendingIntent.getService(
            context, sessionId, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        session.commit(pendingIntent.intentSender)
        session.close()
    }

    override suspend fun requestInstall(context: Context, downloadId: Int, apkFile: File) {
        val intent = Intent(context, SessionInstallerActivity::class.java).apply {
            data = apkFile.toUri()
            putExtra(SessionInstallerActivity.EXTRA_DOWNLOAD_ID, downloadId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    internal suspend fun doInstall(context: Context, downloadId: Int, apkFile: File): Unit =
        withContext(Dispatchers.IO) {
            val sessionID = createSession(context)
            Log.d(LOGGING_TAG, "Created session $sessionID")

            // Actually, if Android asks for permission to install apps, and
            // the user presses "Cancel", then Mitch is not notified of this.
            // So it's better to not call onInstallStart until the very last moment.

            // Otherwise it's impossible to retry the installation (e.g. when Mitch has no
            // permission to install apps on its first launch).
//            Mitch.databaseHandler.onInstallStart(downloadId, sessionID.toLong())
//            Log.d(LOGGING_TAG, "Notified")

            doInstall(apkFile, downloadId, sessionID, context)
            Log.d(LOGGING_TAG, "Installed")
        }

    override suspend fun tryCancel(context: Context, installId: Long): Boolean {
        try {
            context.packageManager.packageInstaller.abandonSession(installId.toInt())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    override suspend fun isInstalling(context: Context, installId: Long): Boolean? {
        return context.packageManager.packageInstaller.getSessionInfo(installId.toInt())?.isActive ?: false
    }
}