package ua.gardenapple.itchupdater.database.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ua.gardenapple.itchupdater.database.AppDatabase

class GameDownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: GameRepository
    val allGames: LiveData<List<Game>>

    init {
        val gamesDao = AppDatabase.getDatabase(app).gameDao
        repository = GameRepository(gamesDao, GameRepository.Type.Downloads)
        allGames = repository.games
    }
}