package ua.gardenapple.itchupdater.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.FLAVOR_FDROID
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.game.GameDao
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.installation.InstallationDao
import ua.gardenapple.itchupdater.database.updatecheck.Converters
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultDao
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel

@Database(
    entities = [Game::class, Installation::class, UpdateCheckResultModel::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val gameDao: GameDao
    abstract val installDao: InstallationDao
    abstract val updateCheckDao: UpdateCheckResultDao

    /**
     * Singleton database
     * https://gist.github.com/florina-muntenescu/697e543652b03d3d2a06703f5d6b44b5
     */
    companion object {
        private const val LOGGING_TAG = "DatabaseSetup"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        
        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "app_database"
            )
                .addMigrations(Migrations.Migration_1_2)
                .addMigrations(Migrations.Migration_2_3)
                .addMigrations(Migrations.Migration_3_4)
                .addMigrations(Migrations.Migration_4_5)
                .addMigrations(Migrations.Migration_5_6)
                .addMigrations(Migrations.Migration_6_7)
                .addMigrations(Migrations.Migration_7_8)
                .addMigrations(Migrations.Migration_8_9)
                .build()
                .also { appDb ->
                    appDb.runInTransaction {
                        Log.d(LOGGING_TAG, "Deleting info on Mitch")
                        appDb.installDao.deleteFinishedInstallation(context.packageName)

                        if (BuildConfig.FLAVOR != FLAVOR_FDROID) {
                            appDb.addMitchToDatabase(context)
                        }

//                            val mitchInstall = appDb.installDao.findInstallation(Game.MITCH_GAME_ID)
//                            Log.d(LOGGING_TAG, "Mitch installation: $mitchInstall")
//
//                            Log.d(LOGGING_TAG, "Known installs:")
//                            val installs = appDb.installDao.getAllInstallationsSync()
//                            for (install in installs)
//                                Log.d(LOGGING_TAG, "$install")
                    }
                }
    }


    fun addMitchToDatabase(context: Context) {
        val game = Game(
            gameId = Game.MITCH_GAME_ID,
            name = "Mitch",
            author = "gardenapple",
            storeUrl = Game.MITCH_STORE_PAGE,
            thumbnailUrl = "https://img.itch.zone/aW1nLzUwODcyNjUucG5n/315x250%23c/bSv0D9.png",
            locale = Game.MITCH_LOCALE
        )
        Log.d(LOGGING_TAG, "Adding game $game")
        gameDao.upsert(game)

        val installation = Installation(
            gameId = Game.MITCH_GAME_ID,
            uploadId = Installation.MITCH_UPLOAD_ID,
            availableUploadIds = null,
            packageName = context.packageName,
            locale = Game.MITCH_LOCALE,
            version = BuildConfig.VERSION_NAME,
            uploadName = Installation.MITCH_UPLOAD_NAME,
            fileSize = Installation.MITCH_FILE_SIZE,
            platformFlags = Installation.PLATFORM_ANDROID
        )
        Log.d(LOGGING_TAG, "Adding install $installation")
        installDao.insert(installation)
    }
}