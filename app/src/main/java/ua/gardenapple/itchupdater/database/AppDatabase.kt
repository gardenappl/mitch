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
import ua.gardenapple.itchupdater.database.game.GameDao
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload
import ua.gardenapple.itchupdater.database.upload.UploadDao
import ua.gardenapple.itchupdater.ioThread

@Database(entities = [Game::class, Upload::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun uploadDao(): UploadDao

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

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)

                        ioThread {
                            var game: Game
                            val gameDao = getDatabase(context).gameDao()

                            if(BuildConfig.FLAVOR == FLAVOR_ITCHIO) {

                                game = Game(544475, "Mitch", "gardenapple", "https://gardenapple.itch.io/mitch",
                                    null, "")
                                gameDao.insert(game)
                            }

                            Log.d(LOGGING_TAG, "Populating database")
                            //TODO: remove test
                            game = Game(17705, "Tanks of Freedom", "P1X", "https://w84death.itch.io/tanks-of-freedom",
                                null, "https://img.itch.zone/aW1hZ2UvMTc3MDUvOTE2NTkuZ2lm/315x250%23cm/ab68U4.gif")
                            gameDao.insert(game)
                        }
                    }
                })
                .build()
    }
}