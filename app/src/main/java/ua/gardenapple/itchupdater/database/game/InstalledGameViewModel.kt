package ua.gardenapple.itchupdater.database.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.GameInstallation

class InstalledGameViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: GameRepository
    val installedGames: LiveData<List<GameInstallation>>

    init {
        val gamesDao = runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).gameDao
        }
        repository = GameRepository(gamesDao, GameRepository.Type.Installed)
        installedGames = repository.games
    }
}