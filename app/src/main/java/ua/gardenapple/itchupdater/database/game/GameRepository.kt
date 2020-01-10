package ua.gardenapple.itchupdater.database.game

import android.util.Log
import androidx.lifecycle.LiveData
import ua.gardenapple.itchupdater.LOGGING_TAG

class GameRepository(private val gameDao: GameDao) {
    val allGames: LiveData<List<GameEntity>> = gameDao.getAllGames()

    suspend fun insert(game: GameEntity) {
        gameDao.insert(game)
    }
}