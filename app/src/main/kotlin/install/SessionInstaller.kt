package garden.appl.mitch.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.app.PendingIntentCompat
import garden.appl.mitch.Mitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

class SessionInstaller : AbstractInstaller() {
    class NotEnoughSpaceException(message: String? = null, cause: IOException? = null) : IOException(message, cause)

    companion object {
        private const val LOGGING_TAG = "SessionInstaller"
    }

    override val type = Type.Stream

    /**
     * Returns the PackageInstaller session ID.
     */
    override fun createSessionForStreamInstall(context: Context): Int {
        Log.d(LOGGING_TAG, "Creating session...")
        val pkgInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        val sessionId = pkgInstaller.createSession(params)
        Log.d(LOGGING_TAG, "Created session $sessionId")
        return sessionId
    }

    override suspend fun openWriteStream(
        context: Context,
        sessionId: Int,
        lengthBytes: Long
    ): OutputStream = withContext(Dispatchers.IO) {
        val pkgInstaller = context.packageManager.packageInstaller

        val session = pkgInstaller.openSession(sessionId)

        try {
            Log.d(LOGGING_TAG, "Bytes: $lengthBytes")
            return@withContext session.openWrite("$sessionId at ${System.nanoTime()}", 0, lengthBytes)
        } catch (e: Exception) {
            session.abandon()
            if (e is IOException && e.message?.startsWith("Failed to allocate") == true) {
                Log.w(LOGGING_TAG, "Looks like not enough space", e)
                throw NotEnoughSpaceException(cause = e)
            } else {
                throw e
            }
        }
    }

    override suspend fun finishStreamInstall(context: Context, sessionId: Int, appName: String) {
        val pkgInstaller = context.packageManager.packageInstaller
        val session = pkgInstaller.openSession(sessionId)

        val callbackIntent = Intent(context, SessionInstallerService::class.java)
        callbackIntent.putExtra(SessionInstallerService.EXTRA_APP_NAME, appName)
        val pendingIntent = PendingIntentCompat.getService(
            context, sessionId, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT, true
        )!!

        Mitch.databaseHandler.onInstallStart(sessionId)
        Log.d(LOGGING_TAG, "Notified")

        session.commit(pendingIntent.intentSender)
        session.close()
    }

    override suspend fun requestInstall(context: Context, downloadId: Long, apkFile: File) {
        throw NotImplementedError()
    }

    override suspend fun tryCancel(context: Context, installId: Long): Boolean {
        try {
            context.packageManager.packageInstaller.abandonSession(installId.toInt())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    override suspend fun isInstalling(context: Context, installId: Long): Boolean {
        return context.packageManager.packageInstaller.getSessionInfo(installId.toInt())?.isActive ?: false
    }
}