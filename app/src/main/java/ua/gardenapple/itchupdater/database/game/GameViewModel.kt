package ua.gardenapple.itchupdater.database.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.database.AppDatabase

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: GameRepository
    val allGames: LiveData<List<GameEntity>>

    init {
        val gamesDao = AppDatabase.getDatabase(app).gameDao()
        repository = GameRepository(gamesDao)
        allGames = repository.allGames
    }

    fun insert(game: GameEntity) = viewModelScope.launch {
        repository.insert(game)
    }
}