package ua.gardenapple.itchupdater.database.game

import android.util.Log
import androidx.lifecycle.LiveData
import ua.gardenapple.itchupdater.LOGGING_TAG

class GameRepository(private val gameDao: GameDao, val type: Type) {
    init {
        Log.d(LOGGING_TAG, "Creating repository, type: $type")
    }

    enum class Type {
        Installed,
        Downloads
    }

    val games: LiveData<List<Game>> = when(type) {
        Type.Downloads -> gameDao.getGameDownloads()
        else -> gameDao.getInstalledAndroidGames()
    }
}