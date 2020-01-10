package ua.gardenapple.itchupdater.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.FLAVOR_ITCHIO
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.database.game.GameDao
import ua.gardenapple.itchupdater.database.game.GameEntity

@Database(entities = arrayOf(GameEntity::class), version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    /*!
        Singleton database
     */
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "game_database"
                ).run {
                    addCallback(AppDatabaseCallback(scope))
                    build()
                }
                INSTANCE = instance
                return instance
            }
        }
    }

    class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { appDatabase ->
                scope.launch {
                    populateDatabase(appDatabase.gameDao())
                }
            }
        }

        suspend fun populateDatabase(gameDao: GameDao) {
            var game: GameEntity
            //TODO: populate with my app
            if(BuildConfig.FLAVOR == FLAVOR_ITCHIO) {

                game = GameEntity(544475, "Mitch", "gardenapple", "https://gardenapple.itch.io/mitch",
                    null, "")
                gameDao.insert(game)
            }

            Log.d(LOGGING_TAG, "Populating database")
            //TODO: remove test
            game = GameEntity(17705, "Tanks of Freedom", "P1X", "https://w84death.itch.io/tanks-of-freedom",
                null, "https://img.itch.zone/aW1hZ2UvMTc3MDUvOTE2NTkuZ2lm/315x250%23cm/ab68U4.gif")
            gameDao.insert(game)
        }
    }
}