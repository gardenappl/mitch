package ua.gardenapple.itchupdater.install

import android.content.Context
import java.io.File

abstract class AbstractInstaller {
    /**
     * Start installing APK from private storage.
     * Implementations should call [InstallerDatabaseHandler.onInstallStart] when they have a
     * meaningful "install ID", and call [Installations.onInstallResult] on completion.
     */
    abstract suspend fun requestInstall(context: Context, downloadId: Int, apkFile: File)

    /**
     * @return true if installation was cancelled
     */
    abstract suspend fun tryCancel(context: Context, installId: Long): Boolean

    /**
     * @return true if installation is in progress, null if impossible to tell
     */
    abstract suspend fun isInstalling(context: Context, installId: Long): Boolean?
}
