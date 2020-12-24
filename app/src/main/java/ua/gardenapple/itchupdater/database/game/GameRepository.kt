package ua.gardenapple.itchupdater.database.game

import androidx.lifecycle.LiveData

class GameRepository(private val gameDao: GameDao, val type: Type) {
    enum class Type {
        Installed,
        Downloads,
        Pending
    }

    val games: LiveData<List<GameInstallation>> = when(type) {
        Type.Downloads -> gameDao.getGameDownloads()
        Type.Installed -> gameDao.getInstalledAndroidGames()
        Type.Pending -> gameDao.getPendingGames()
    }
}