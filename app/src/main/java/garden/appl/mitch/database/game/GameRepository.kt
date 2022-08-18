package garden.appl.mitch.database.game

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import garden.appl.mitch.database.installation.GameInstallation

class GameRepository(gameDao: GameDao, val type: Type) {
    enum class Type {
        Installed,
        Downloads,
        Pending,
        WebCached
    }

    val games: LiveData<List<GameInstallation>> = when(type) {
        Type.Downloads -> gameDao.getGameDownloads()
        Type.Installed -> gameDao.getInstalledAndroidGames()
        Type.Pending -> gameDao.getPendingGames()
        Type.WebCached -> gameDao.getCachedWebGames().map { games ->
            games.map(GameInstallation::createCachedWebGameInstallation)
        }
    }
}