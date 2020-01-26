package ua.gardenapple.itchupdater.database.game

import android.util.Log
import androidx.lifecycle.LiveData

class GameRepository(private val gameDao: GameDao, val type: Type) {
    enum class Type {
        Installed,
        Downloads
    }

    val games: LiveData<List<Game>> = when(type) {
        Type.Downloads -> gameDao.getGameDownloads()
        else -> gameDao.getInstalledAndroidGames()
    }
}