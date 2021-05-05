package ua.gardenapple.itchupdater.database

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.PREF_DB_RAN_CLEANUP_ONCE
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation


class DatabaseCleanup(private val context: Context) {
    companion object {
        private const val LOGGING_TAG = "CleanupWorker"
    }

    suspend fun cleanAppDatabase(db: AppDatabase): Result = withContext(Dispatchers.IO) {
        val packageInstaller = context.packageManager.packageInstaller
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        Log.d(LOGGING_TAG, "Started.")

        val installs = db.installDao.getAllKnownInstallationsSync().toMutableList()
        val installsToDelete = ArrayList<Installation>()

        for (install in installs) {
            Log.d(LOGGING_TAG, "Install: $install")
            when (install.status) {
                Installation.STATUS_INSTALLED -> install.packageName?.let { packageName ->
                    if (!Utils.isPackageInstalled(packageName, context.packageManager))
                        installsToDelete.add(install)
                }
                Installation.STATUS_INSTALLING -> {
                    val installSessionId = install.downloadOrInstallId?.toInt()
                    if (installSessionId == null)
                        installsToDelete.add(install)
                    else if (packageInstaller.getSessionInfo(installSessionId) == null)
                        installsToDelete.add(install)
                }
                Installation.STATUS_DOWNLOADING -> {
                    if (!Mitch.fileManager.checkIsDownloading(context, install))
                        installsToDelete.add(install)
                }
            }
        }

        installsToDelete.forEach {
            Log.d(LOGGING_TAG, "To remove: $it")
        }
        if (installsToDelete.isNotEmpty()) {
            db.installDao.delete(installsToDelete)
            installs.removeAll(installsToDelete)
        }


        val installsToUpdate = ArrayList<Installation>()
        //TODO: Backwards compatibility with older versions of Mitch
        if (sharedPrefs.getBoolean(PREF_DB_RAN_CLEANUP_ONCE, false)) {
            for (i in installs.indices) {
                var install = installs[i]
                if (install.packageName != null && install.platforms and
                    Installation.PLATFORM_ANDROID == 0) {

                    install = install.copy(
                        platforms = install.platforms or Installation.PLATFORM_ANDROID
                    )
                    installsToUpdate.add(install)
                }
            }
        }

        for (install in installsToUpdate) {
            Log.d(LOGGING_TAG, "To update: $install")
        }
        if (installsToUpdate.isNotEmpty())
            db.installDao.update(installsToUpdate)


        val games = db.gameDao.getAllKnownGamesSync().toMutableList()
        val gamesToDelete = ArrayList<Game>()
        for (game in games) {
            Log.d(LOGGING_TAG, "Game: $game")
            if (installs.find { install -> install.gameId == game.gameId } == null)
                gamesToDelete.add(game)
        }

        gamesToDelete.forEach {
            Log.d(LOGGING_TAG, "To remove: $it")
        }
        if (gamesToDelete.isNotEmpty()) {
            db.gameDao.delete(gamesToDelete)
            games.removeAll(gamesToDelete)
        }

        Log.d(LOGGING_TAG, "Done.")

        sharedPrefs.edit().run {
            putBoolean(PREF_DB_RAN_CLEANUP_ONCE, true)
            commit()
        }

        Result.success()
    }


    class Worker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.withTransaction {
                DatabaseCleanup(applicationContext).cleanAppDatabase(db)
            }
        }
    }
}