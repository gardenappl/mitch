package garden.appl.mitch.install

import android.content.Context
import java.io.File
import java.io.OutputStream

abstract class AbstractInstaller {
    enum class Type {
        Stream,
        File
    }
    abstract val type: Type

    /**
     * @throws NotImplementedError if [type] is [Type.File].
     */
    abstract fun createSessionForStreamInstall(context: Context): Int
    
    /**
     * Write an APK file into the install session. The caller should then call [finishStreamInstall].
     *
     * @param lengthBytes file size, -1 if unknown
     * @throws NotImplementedError if [type] is [Type.File].
     */
    abstract suspend fun openWriteStream(context: Context, sessionId: Int, lengthBytes: Long): OutputStream

    /**
     * Implementations should call [InstallationDatabaseManager.onInstallStart] at the beginning,
     * and [Installations.onInstallResult] on completion.
     *
     * @throws NotImplementedError if [type] is [Type.File].
     */
    abstract suspend fun finishStreamInstall(context: Context, sessionId: Int, appName: String)

    /**
     * @throws NotImplementedError if [type] is [Type.Stream]
     */
    abstract suspend fun requestInstall(context: Context, downloadId: Long, apkFile: File)

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
