package ua.gardenapple.itchupdater.database.updatecheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ua.gardenapple.itchupdater.database.AppDatabase

class UpdateCheckResultViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: UpdateCheckResultRepository =
        UpdateCheckResultRepository(AppDatabase.getDatabase(app).updateCheckDao)

    val availableUpdates = repository.availableUpdates
}