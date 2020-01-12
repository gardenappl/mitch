package ua.gardenapple.itchupdater.database.game

import androidx.lifecycle.LiveData

class GameRepository(private val gameDao: GameDao) {
    val allGames: LiveData<List<Game>> = gameDao.getAllGames()

    fun insert(game: Game) {
        gameDao.insert(game)
    }
}