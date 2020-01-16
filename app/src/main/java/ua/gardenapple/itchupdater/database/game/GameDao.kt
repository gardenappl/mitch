package ua.gardenapple.itchupdater.database.game

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.gardenapple.itchupdater.database.game.Game.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.game.Game.Companion.TABLE_NAME

@Dao
interface GameDao {
    @Query("SELECT * FROM $TABLE_NAME")
    fun getAllGames(): LiveData<List<Game>>

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId LIMIT 1")
    fun getGameById(gameId: Int): Game

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg games: Game)
}