package garden.appl.mitch.database

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import garden.appl.mitch.BuildConfig
import garden.appl.mitch.FLAVOR_FDROID
import garden.appl.mitch.PREF_DB_RAN_CLEANUP_ONCE
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.game.GameDao
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.database.installation.InstallationDao
import garden.appl.mitch.database.updatecheck.Converters
import garden.appl.mitch.database.updatecheck.UpdateCheckResultDao
import garden.appl.mitch.database.updatecheck.UpdateCheckResultModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Database(
    entities = [Game::class, Installation::class, UpdateCheckResultModel::class],
    version = 16
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val gameDao: GameDao
    abstract val installDao: InstallationDao
    abstract val updateCheckDao: UpdateCheckResultDao

    companion object {
        private const val LOGGING_TAG = "DatabaseSetup"

        private val creationMutex = Mutex()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        suspend fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: creationMutex.withLock {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        
        private suspend fun buildDatabase(context: Context): AppDatabase = withContext(Dispatchers.IO) {
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "app_database"
            ).run {
                for (migration in Migrations)
                    addMigrations(migration)
                build()
            }.also { appDb ->
                appDb.addMitchToDatabaseIfNeeded(context)

                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
                if (!sharedPrefs.getBoolean(PREF_DB_RAN_CLEANUP_ONCE, false)) {
                    DatabaseCleanup(context).cleanAppDatabase(appDb)
                }
            }
        }
    }


    suspend fun addMitchToDatabaseIfNeeded(context: Context) {
        if (BuildConfig.FLAVOR == FLAVOR_FDROID) {
            installDao.deleteFinishedInstallation(context.packageName)
            return
        }

        val game = Game(
            gameId = Game.MITCH_GAME_ID,
            name = "Mitch",
            author = "gardenapple",
            storeUrl = Game.MITCH_STORE_PAGE,
            thumbnailUrl = "https://img.itch.zone/aW1nLzY2OTY1OTIucG5n/315x250%23c/iuehUL.png",
            downloadPageUrl = null,
            webEntryPoint = null,
            webIframe = null,
            faviconUrl = null
        )
        Log.d(LOGGING_TAG, "Adding Mitch game $game")
        gameDao.upsert(game)

        //Try to find already existing Installation for Mitch
        val install = installDao.getInstallationByPackageName(context.packageName)
        Log.d(LOGGING_TAG, "Current Mitch install is $install")

        val newInstall = Installation(
            internalId = install?.internalId ?: 0,
            gameId = Game.MITCH_GAME_ID,
            uploadId = Installation.MITCH_UPLOAD_ID,
            availableUploadIds = null,
            packageName = context.packageName,
            version = BuildConfig.VERSION_NAME,
            uploadName = Installation.MITCH_UPLOAD_NAME,
            fileSize = Installation.MITCH_FILE_SIZE,
            platforms = Installation.PLATFORM_ANDROID
        )
        Log.d(LOGGING_TAG, "Upserting Mitch install: $newInstall")
        installDao.upsert(newInstall)
    }
}