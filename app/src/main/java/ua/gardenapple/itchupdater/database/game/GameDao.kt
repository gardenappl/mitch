package ua.gardenapple.itchupdater.database.game

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.game.Game.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.game.Game.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.installation.Installation

data class GameWithInstallationStatus(@Embedded val game: Game, val status: Int)

@Dao
abstract class GameDao {
    @Query("SELECT * FROM $TABLE_NAME")
    abstract fun getAllKnownGames(): LiveData<List<Game>>

    @Query("""
        SELECT $TABLE_NAME.*, ${Installation.TABLE_NAME}.${Installation.STATUS} AS status
        FROM $TABLE_NAME INNER JOIN ${Installation.TABLE_NAME}
        ON $TABLE_NAME.$GAME_ID = ${Installation.TABLE_NAME}.${Installation.GAME_ID}
        WHERE ${Installation.STATUS} = ${Installation.STATUS_INSTALLED}""")
    abstract fun getAllInstalledGames(): LiveData<List<GameWithInstallationStatus>>


    @Query("""
        SELECT $TABLE_NAME.*, ${Installation.TABLE_NAME}.${Installation.STATUS} AS status
        FROM $TABLE_NAME INNER JOIN ${Installation.TABLE_NAME}
        ON $TABLE_NAME.$GAME_ID = ${Installation.TABLE_NAME}.${Installation.GAME_ID}
        WHERE ${Installation.STATUS} = ${Installation.STATUS_INSTALLED} AND
            ${Installation.PACKAGE_NAME} IS NOT NULL""")
    abstract fun getInstalledAndroidGames(): LiveData<List<GameWithInstallationStatus>>


    @Query("""
        SELECT $TABLE_NAME.*, ${Installation.TABLE_NAME}.${Installation.STATUS} AS status
        FROM $TABLE_NAME INNER JOIN ${Installation.TABLE_NAME}
        ON $TABLE_NAME.$GAME_ID = ${Installation.TABLE_NAME}.${Installation.GAME_ID}
        WHERE ${Installation.STATUS} = ${Installation.STATUS_INSTALLED} AND
            ${Installation.PACKAGE_NAME} IS NULL""")
    abstract fun getGameDownloads(): LiveData<List<GameWithInstallationStatus>>


    @Query("""
        SELECT $TABLE_NAME.*, ${Installation.TABLE_NAME}.${Installation.STATUS} AS status
        FROM $TABLE_NAME INNER JOIN ${Installation.TABLE_NAME}
        ON $TABLE_NAME.$GAME_ID = ${Installation.TABLE_NAME}.${Installation.GAME_ID}
        WHERE ${Installation.STATUS} != ${Installation.STATUS_INSTALLED}""")
    abstract fun getPendingGames(): LiveData<List<GameWithInstallationStatus>>


    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId LIMIT 1")
    abstract fun getGameById(gameId: Int): Game?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun insert(vararg games: Game)

    @Update
    abstract fun update(vararg games: Game)

    @Transaction
    open fun upsert(vararg games: Game) {
        for(game in games) {
            val existingGame = getGameById(game.gameId)
            if (existingGame == null)
                insert(game)
            else {
                update(game)
            }
        }
    }
}