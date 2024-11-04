package garden.appl.mitch.install

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import garden.appl.mitch.*
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import java.io.File

object Installations {
    private const val LOGGING_TAG = "Installations"
    internal val nativeInstaller = NativeInstaller()
    internal val sessionInstaller = SessionInstaller()


    suspend fun deleteFinishedInstall(context: Context, uploadId: Int) {
        val db = AppDatabase.getDatabase(context)
        db.installDao.deleteFinishedInstallation(uploadId)

        withContext(Dispatchers.IO) {
            Mitch.fileManager.deleteDownloadedFile(uploadId)
        }
    }

    suspend fun deleteOutdatedInstalls(context: Context, pendingInstall: Installation) {
        deleteFinishedInstall(context, pendingInstall.uploadId)

        if (pendingInstall.availableUploadIds == null)
            return

        val db = AppDatabase.getDatabase(context)
        val finishedInstalls =
            db.installDao.getFinishedInstallationsForGame(pendingInstall.gameId)

        for (finishedInstall in finishedInstalls) {
            if (!pendingInstall.availableUploadIds.contains(finishedInstall.uploadId))
                deleteFinishedInstall(context, finishedInstall.uploadId)
        }
    }

    suspend fun cancelPending(context: Context, pendingInstall: Installation) =
        cancelPending(
            context,
            pendingInstall.status,
            pendingInstall.downloadOrInstallId!!,
            pendingInstall.uploadId,
            pendingInstall.internalId
        )

    suspend fun cancelPending(
        context: Context,
        status: Int,
        downloadOrInstallId: Long,
        uploadId: Int,
        installId: Int
    ) {
        if (status == Installation.STATUS_INSTALLED && status == Installation.STATUS_WEB_CACHED)
            throw IllegalArgumentException("Tried to cancel installed Installation")

        if (status != Installation.STATUS_INSTALLING) {
            val notificationService =
                context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
            if (Utils.fitsInInt(downloadOrInstallId))
                notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD, downloadOrInstallId.toInt())
            else
                notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD_LONG, downloadOrInstallId.toInt())
        }

        val db = AppDatabase.getDatabase(context)
        db.updateCheckDao.getUpdateCheckResultForUpload(uploadId)?.let {
            it.isInstalling = false
            db.updateCheckDao.insert(it)
        }

        if (status == Installation.STATUS_INSTALLING) {
            if (getInstaller(downloadOrInstallId).tryCancel(context, downloadOrInstallId))
                return
        }

        if (status == Installation.STATUS_DOWNLOADING) {
            Log.d(LOGGING_TAG, "Cancelling $downloadOrInstallId")
            Mitch.fileManager.cancel(context, downloadOrInstallId, uploadId)
        } else {
            withContext(Dispatchers.IO) {
                Mitch.fileManager.deletePendingFile(uploadId)
            }
        }
        db.installDao.delete(installId)
    }

    fun getInstaller(installId: Long): AbstractInstaller {
        return if (Utils.fitsInInt(installId))
            sessionInstaller
        else
            nativeInstaller
    }

    fun getInstaller(context: Context): AbstractInstaller {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        val defaultInstaller = if (MiuiUtils.doesSessionInstallerWork())
            "session"
        else
            "native"

        return if (sharedPrefs.getString(PREF_INSTALLER, defaultInstaller) == "native")
            nativeInstaller
        else
            sessionInstaller
    }

    suspend fun onInstallResult(context: Context, installId: Long, appName: String,
                                packageName: String?, apk: File?, status: Int) {
        var packageName = packageName

        val db = AppDatabase.getDatabase(context)
        val install = db.installDao.getPendingInstallationByInstallId(installId)

        //TODO: this seems to only happen when
        // 1. we request permission to install
        // 2. Android launches installation twice???
        // 3. First one finishes, pending install is deleted
        // 4. Second one fails and install is null
        // This is a workaround, but is there a better solution? Or maybe the bug is my fault?
        if (install == null)
            return

        if (status == PackageInstaller.STATUS_SUCCESS && packageName == null) {
            if (install.packageName != null)
                packageName = install.packageName
            else
                packageName = tryGetPackageName(context, apk!!.path)!!
        }

        notifyInstallResult(context, installId, packageName, appName, status)
        Mitch.fileManager.deletePendingFile(install.uploadId)
        Mitch.databaseHandler.onInstallResult(install, packageName, status)
    }

    /**
     * This method should *NOT* depend on the AppDatabase because this could be used for
     * the GitLab build update check, or other things
     */
    private fun notifyInstallResult(
        context: Context, installSessionId: Long,
        packageName: String?, appName: String, status: Int
    ) {
        val message = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.resources.getString(R.string.notification_install_cancelled_title)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> context.resources.getString(R.string.notification_install_blocked_title)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> context.resources.getString(R.string.notification_install_conflict_title)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> context.resources.getString(R.string.notification_install_incompatible_title)
            PackageInstaller.STATUS_FAILURE_INVALID -> context.resources.getString(R.string.notification_install_invalid_title)
            PackageInstaller.STATUS_FAILURE_STORAGE -> context.resources.getString(R.string.notification_install_storage_title)
            PackageInstaller.STATUS_SUCCESS -> context.resources.getString(R.string.notification_install_complete_title)
            else -> context.resources.getString(R.string.notification_install_unknown_title)
        }
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INSTALLING).apply {
                setSmallIcon(R.drawable.ic_mitch_notification)
                setContentText(message)

                if (status == PackageInstaller.STATUS_SUCCESS) {
                    try {
                        val appInfo = context.packageManager.getApplicationInfo(packageName!!, 0)
                        setContentTitle(context.packageManager.getApplicationLabel(appInfo))
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(LOGGING_TAG, "Error: no name for package name $packageName", e)
                        setContentTitle(appName)
                    }

                    packageName?.let {
                        context.packageManager.getLaunchIntentForPackage(it)?.also { intent ->
                            val pendingIntent =
                                PendingIntentCompat.getActivity(context, 0, intent, 0, false)
                            setContentIntent(pendingIntent)
                            setAutoCancel(true)
                        }
                    }

                    try {
                        val icon = context.packageManager.getApplicationIcon(packageName!!)
                        setLargeIcon(Utils.drawableToBitmap(icon))
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(LOGGING_TAG, "Could not load icon for package name $packageName", e)
                    }
                } else {
                    setContentTitle(appName)
                }
//                priority = NotificationCompat.PRIORITY_HIGH
            }

        with(NotificationManagerCompat.from(context)) {
            if (Utils.fitsInInt(installSessionId))
                notify(NOTIFICATION_TAG_INSTALL_RESULT, installSessionId.toInt(), builder.build())
            else
                notify(NOTIFICATION_TAG_INSTALL_RESULT_LONG, installSessionId.toInt(),
                    builder.build())
        }
    }

    suspend fun tryUpdatePendingInstallData(context: Context, installId: Long, apk: File) {
        val db = AppDatabase.getDatabase(context)
        val install = db.installDao.getPendingInstallationByInstallId(installId)!!
        db.installDao.update(install.copy(
            packageName = tryGetPackageName(context, apk.path)
        ))
    }

    private fun tryGetPackageName(context: Context, apkPath: String): String? {
        Log.d(LOGGING_TAG, "Looking at package info for $apkPath")
        val packageInfo = context.packageManager.getPackageArchiveInfo(apkPath, 0)
        Log.d(LOGGING_TAG, "pkg info: $packageInfo")
        return packageInfo?.packageName
    }
}