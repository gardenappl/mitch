package ua.gardenapple.itchupdater.database.game

import android.util.Log
import androidx.lifecycle.LiveData

class GameRepository(private val gameDao: GameDao, val type: Type) {
    enum class Type {
        Installed,
        Downloads,
        Pending
    }

    val games: LiveData<List<GameWithInstallationStatus>> = when(type) {
        Type.Downloads -> gameDao.getGameDownloads()
        Type.Installed -> gameDao.getInstalledAndroidGames()
        Type.Pending -> gameDao.getPendingGames()
    }
}