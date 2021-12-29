package ua.gardenapple.itchupdater.install

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.File

object Installations {
    private const val LOGGING_TAG = "Installations"
    internal val nativeInstaller = NativeInstaller()
    internal val sessionInstaller = SessionInstaller()


    suspend fun downloadOrInstall() {

    }

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
        if (status == Installation.STATUS_INSTALLED)
            throw IllegalArgumentException("Tried to cancel installed Installation")

        if (status != Installation.STATUS_INSTALLING) {
            val notificationService =
                context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD, downloadOrInstallId.toInt())
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

    fun getInstaller(context: Context, install: Installation): AbstractInstaller {
        return when (install.status) {
            Installation.STATUS_INSTALLING -> getInstaller(install.downloadOrInstallId!!)
            Installation.STATUS_READY_TO_INSTALL -> getInstaller(context)
            else -> throw IllegalArgumentException("Can't get installer for $install")
        }
    }

    fun getInstaller(installId: Long): AbstractInstaller {
        return if (Utils.fitsInInt(installId))
            sessionInstaller
        else
            nativeInstaller
    }

    fun getInstaller(context: Context): AbstractInstaller {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return if (sharedPrefs.getString(PREF_INSTALLER, "native") == "native")
            nativeInstaller
        else
            sessionInstaller
    }

    suspend fun onInstallResult(context: Context, installId: Long,
                                packageName: String?, apk: File?, status: Int) {
        var packageName = packageName

        val db = AppDatabase.getDatabase(context)
        val install = db.installDao.getPendingInstallationByInstallId(installId)!!

        if (status == PackageInstaller.STATUS_SUCCESS && packageName == null) {
            if (install.packageName != null)
                packageName = install.packageName
            else
                packageName = tryGetPackageName(context, apk!!.path)!!
        }

        notifyInstallResult(context, installId, packageName, apk?.name, status)
        Mitch.fileManager.deletePendingFile(install.uploadId)
        deleteOutdatedInstalls(context, install)
        Mitch.databaseHandler.onInstallResult(install, packageName, status)

        db.updateCheckDao.getUpdateCheckResultForUpload(install.uploadId)?.let {
            it.isInstalling = false
            db.updateCheckDao.insert(it)
        }
    }

    /**
     * This method should *NOT* depend on the AppDatabase because this could be used for
     * the GitLab build update check, or other things
     */
    private fun notifyInstallResult(
        context: Context, installSessionId: Long,
        packageName: String?, apkName: String?, status: Int
    ) {
        val message = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.resources.getString(R.string.notification_install_cancelled_title)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> context.resources.getString(R.string.notification_install_blocked_title)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> context.resources.getString(R.string.notification_install_conflict_title)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> context.resources.getString(R.string.notification_install_incompatible_title)
            PackageInstaller.STATUS_FAILURE_INVALID -> context.resources.getString(R.string.notification_install_invalid_title)
            PackageInstaller.STATUS_FAILURE_STORAGE -> context.resources.getString(R.string.notification_install_storage_title)
            PackageInstaller.STATUS_SUCCESS -> context.resources.getString(R.string.notification_install_complete_title)
            else -> context.resources.getString(R.string.notification_install_complete_title)
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
                        setContentTitle(apkName)
                    }

                    packageName?.let {
                        context.packageManager.getLaunchIntentForPackage(it)?.also { intent ->
                            val pendingIntent =
                                PendingIntent.getActivity(context, 0, intent, 0)
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
                    setContentTitle(apkName)
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