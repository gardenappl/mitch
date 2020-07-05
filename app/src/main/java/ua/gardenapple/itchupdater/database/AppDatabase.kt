package ua.gardenapple.itchupdater.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.FLAVOR_ITCHIO
import ua.gardenapple.itchupdater.database.game.GameDao
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.installation.InstallationDao
import ua.gardenapple.itchupdater.database.upload.Upload
import ua.gardenapple.itchupdater.database.upload.UploadDao
import ua.gardenapple.itchupdater.ioThread

@Database(
    entities = [Game::class, Upload::class, Installation::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract val gameDao: GameDao
    abstract val uploadDao: UploadDao
    abstract val installDao: InstallationDao


    /**
     * Singleton database
     * https://gist.github.com/florina-muntenescu/697e543652b03d3d2a06703f5d6b44b5
     */
    companion object {
        const val LOGGING_TAG = "DatabaseSetup"
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
                .addCallback(object : Callback() {

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        Log.d(LOGGING_TAG, "Opening database...")

                        ioThread {
                            val appDb = getDatabase(context)
                            if (BuildConfig.FLAVOR == FLAVOR_ITCHIO) {
                                appDb.addMitchToDatabase(context)
                            } else {
                                Log.d(LOGGING_TAG, "Deleting info on Mitch")
                                appDb.uploadDao.clearAllUploadsForGame(Game.MITCH_GAME_ID)
                                appDb.installDao.clearAllInstallationsForGame(Game.MITCH_GAME_ID)
                            }

                            /*val mitchInstall = appDb.installDao.findInstallation(Game.MITCH_GAME_ID)
                            Log.d(LOGGING_TAG, "Mitch installation: $mitchInstall")

                            Log.d(LOGGING_TAG, "Known installs:")
                            val installs = appDb.installDao.getAllInstallationsSync()
                            for (install in installs)
                                Log.d(LOGGING_TAG, "$install")

                            Log.d(LOGGING_TAG, "Known uploads:")
                            val uploads = appDb.uploadDao.getAllUploadsSync()
                            for (upload in uploads)
                                Log.d(LOGGING_TAG, "$upload")*/
                        }
                    }
                })
                .addMigrations(Migrations.Migration_1_2)
                .build()

    }


    fun addMitchToDatabase(context: Context) {
        val game = Game(
            gameId = Game.MITCH_GAME_ID,
            name = "Mitch",
            author = "gardenapple",
            storeUrl = "https://gardenapple.itch.io/mitch",
            thumbnailUrl = "",  //TODO: thumbnail URL
            locale = Game.MITCH_LOCALE
        )
        Log.d(LOGGING_TAG, "Adding game $game")
        gameDao.upsert(game)

        val upload = Upload(
            uploadId = Upload.MITCH_UPLOAD_ID,
            gameId = Game.MITCH_GAME_ID,
            version = BuildConfig.VERSION_NAME,
            locale = Game.MITCH_LOCALE,
            name = Upload.MITCH_RELEASE_NAME,
            fileSize = Upload.MITCH_FILE_SIZE,
            platforms = Upload.PLATFORM_ANDROID
        )
        uploadDao.clearAllUploadsForGame(Game.MITCH_GAME_ID)
        Log.d(LOGGING_TAG, "Adding upload $upload")
        uploadDao.insert(upload)

        installDao.clearAllInstallationsForGame(Game.MITCH_GAME_ID)
        val installation = Installation(
            gameId = Game.MITCH_GAME_ID,
            uploadId = Upload.MITCH_UPLOAD_ID,
            packageName = context.packageName
        )
        Log.d(LOGGING_TAG, "Adding install $installation")
        installDao.insert(installation)
    }
}