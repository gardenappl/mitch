package garden.appl.mitch.database.updatecheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import garden.appl.mitch.database.AppDatabase

class UpdateCheckResultViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: UpdateCheckResultRepository = runBlocking(Dispatchers.IO) {
        UpdateCheckResultRepository(AppDatabase.getDatabase(app).updateCheckDao)
    }

    val availableUpdates = repository.availableUpdates
}