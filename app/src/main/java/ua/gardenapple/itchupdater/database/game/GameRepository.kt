package ua.gardenapple.itchupdater.database.game

import androidx.lifecycle.LiveData

class GameRepository(private val gameDao: GameDao) {
    val allGames: LiveData<List<GameEntity>> = gameDao.getAllGames()

    suspend fun insert(game: GameEntity) {
        gameDao.insert(game)
    }
}