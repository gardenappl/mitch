package ua.gardenapple.itchupdater.database

import android.content.Context
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

@Database(entities = [Game::class, Upload::class, Installation::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun uploadDao(): UploadDao
    abstract val installationDao: InstallationDao

    /**
     * Singleton database
     * https://gist.github.com/florina-muntenescu/697e543652b03d3d2a06703f5d6b44b5
     */
    companion object {
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
                        ioThread {
                            val appDb = getDatabase(context)

                            if(BuildConfig.FLAVOR == FLAVOR_ITCHIO) {
                                val game = Game(Game.MITCH_GAME_ID, "Mitch", "gardenapple", "https://gardenapple.itch.io/mitch",
                                    null, "")
                                appDb.gameDao().insert(game)

                                val upload = Upload(null, Game.MITCH_GAME_ID, 0, "1.0", "[Mitch release name]", "[Mitch file size]")
                                appDb.uploadDao().clearUploadsForGame(Game.MITCH_GAME_ID)
                                appDb.uploadDao().insert(upload)

                                val installation = Installation(Installation.MITCH_UPLOAD_ID, null, context.packageName)
                                appDb.installationDao.insert(installation)
                            }
                        }
                    }
                })
                .addMigrations(
                    Migrations.Migration_1_2,
                    Migrations.Migration_2_3
                ).build()
    }
}