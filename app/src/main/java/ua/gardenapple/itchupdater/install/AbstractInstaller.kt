package ua.gardenapple.itchupdater.install

import android.content.Context
import java.io.File
import java.io.InputStream

abstract class AbstractInstaller {
    /**
     * Start quietly installing APK from a byte stream.
     * If [acceptsInstallFromStream] returns false then this may throw a [NotImplementedError]
     * Implementations should call [InstallerDatabaseHandler.onInstallStart] when they have a
     * meaningful "install ID", and call [Installations.onInstallResult] on completion.
     */
    abstract suspend fun installFromStream(context: Context, downloadId: Int, apkStream: InputStream, lengthBytes: Long)

    abstract fun acceptsInstallFromStream(): Boolean

    /**
     * Same as the other [requestInstall] but works with a file in private storage.
     * Unlike the stream-based version, this should be supported by all installers.
     *
     * This should exit quickly, and may start a new Intent if needed.
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
