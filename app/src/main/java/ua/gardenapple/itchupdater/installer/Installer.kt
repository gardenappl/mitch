package ua.gardenapple.itchupdater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import com.bumptech.glide.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Utils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.*

class Installer {
    companion object {
        const val LOGGING_TAG = "Installer"
    }

    /**
     * Returns the PackageInstaller session ID.
     */
    fun createSession(context: Context): Int {
        Log.d(LOGGING_TAG, "Creating session...")
        val pkgInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        return pkgInstaller.createSession(params)
    }

    suspend fun install(apkUri: Uri, sessionId: Int, context: Context) = withContext(Dispatchers.IO) {
        val pkgInstaller = context.packageManager.packageInstaller

        val session = pkgInstaller.openSession(sessionId)

        val apkFile = File(apkUri.path)

        try {
            apkFile.inputStream().use { inputStream ->
                session.openWrite(apkFile.name, 0, apkFile.length()).use { outputStream ->
                    Utils.copy(inputStream, outputStream)
                    session.fsync(outputStream)
                }
            }
        } catch (e: IOException) {
            session.abandon()
            Log.e(LOGGING_TAG, "Error occurred while creating install session", e)
        }

        val callbackIntent = Intent(context, InstallerService::class.java)
        val pendingIntent = PendingIntent.getService(
            context, sessionId, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}