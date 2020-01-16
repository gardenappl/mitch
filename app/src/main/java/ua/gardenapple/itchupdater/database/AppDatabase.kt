package ua.gardenapple.itchupdater.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.FLAVOR_ITCHIO
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.database.game.GameDao
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.installation.InstallationDao
import ua.gardenapple.itchupdater.database.upload.Upload
import ua.gardenapple.itchupdater.database.upload.UploadDao
import ua.gardenapple.itchupdater.ioThread

@Database(entities = [Game::class, Upload::class, Installation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract val gameDao: GameDao
    abstract val uploadDao: UploadDao
    abstract val installationDao: InstallationDao

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
            Room.databaseBuilder(context.applicationContext,
                AppDatabase::class.java, "app_database")
                .addCallback(object : Callback() {

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        Log.d(LOGGING_TAG, "Opening database...")
                        ioThread {
                            val appDb = getDatabase(context)
                            val locale = Game.MITCH_LOCALE

                            if(BuildConfig.FLAVOR == FLAVOR_ITCHIO) {
                                val game = Game(
                                    gameId = Game.MITCH_GAME_ID,
                                    name = "Mitch",
                                    author = "gardenapple",
                                    storeUrl = "https://gardenapple.itch.io/mitch",
                                    thumbnailUrl = "",  //TODO: thumbnail URL
                                    locale = locale
                                )
                                Log.d(LOGGING_TAG, "Adding game $game")
                                appDb.gameDao.insert(game)

                                val upload = Upload(
                                    uploadId = null,
                                    gameId = Game.MITCH_GAME_ID,
                                    uploadNum = 0,
                                    version = BuildConfig.VERSION_NAME,
                                    locale = locale,
                                    name = "[Mitch release name]",
                                    fileSize = "[Mitch file size]"
                                )
                                appDb.uploadDao.clearUploadsForGame(Game.MITCH_GAME_ID)
                                Log.d(LOGGING_TAG, "Adding upload $upload")
                                appDb.uploadDao.insert(upload)

                                val installation = Installation(
                                    gameId = Game.MITCH_GAME_ID,
                                    uploadIdInternal = Installation.MITCH_UPLOAD_ID,
                                    packageName = context.packageName,
                                    downloadFinished = true,
                                    locale = locale
                                )
                                appDb.installationDao.insert(installation)
                            } else {
                                Log.d(LOGGING_TAG, "Deleting info on Mitch")
                                appDb.uploadDao.clearUploadsForGame(Game.MITCH_GAME_ID)
                            }

                            val mitchInstall = appDb.installationDao.findInstallation(Game.MITCH_GAME_ID)
                            Log.d(LOGGING_TAG, "Mitch installation: $mitchInstall")
                        }
                    }
                })
                .build()
    }
}