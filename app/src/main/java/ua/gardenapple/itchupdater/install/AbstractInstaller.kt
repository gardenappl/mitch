package ua.gardenapple.itchupdater.install

import android.content.Context
import java.io.File

abstract class AbstractInstaller {
    /**
     * Start installing APK from private storage.
     * Implementations should call [InstallerDatabaseHandler.onInstallStart] when they have a
     * meaningful "install ID", and call [Installations.onInstallResult] on completion.
     *
     * Implementations will probably need to start a new [Intent]
     */
    abstract suspend fun requestInstall(context: Context, downloadId: Int, apkFile: File)

    /**
     * @return true if installation was cancelled. If true, the installation file
     * will be deleted and [Installations.onInstallResult] will be called. Otherwise,
     * this should be done by the caller!
     */
    abstract suspend fun tryCancel(context: Context, installId: Long): Boolean

    /**
     * @return true if installation is in progress, null if impossible to tell
     */
    abstract suspend fun isInstalling(context: Context, installId: Long): Boolean?
}
