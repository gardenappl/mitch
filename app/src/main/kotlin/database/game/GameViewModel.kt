package garden.appl.mitch.database.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.GameInstallation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal open class GameViewModel(app: Application, type: GameRepository.Type) : AndroidViewModel(app) {
    private val repository: GameRepository
    val games: LiveData<List<GameInstallation>>

    init {
        val gamesDao = runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).gameDao
        }
        repository = GameRepository(gamesDao, type)
        games = repository.games
    }

    class Pending(app: Application) : GameViewModel(app, GameRepository.Type.Pending)
    class Installed(app: Application) : GameViewModel(app, GameRepository.Type.Installed)
    class Downloads(app: Application) : GameViewModel(app, GameRepository.Type.Downloads)
    class WebCached(app: Application) : GameViewModel(app, GameRepository.Type.WebCached)
}