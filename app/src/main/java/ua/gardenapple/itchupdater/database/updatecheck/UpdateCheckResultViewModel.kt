package ua.gardenapple.itchupdater.database.updatecheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.database.AppDatabase

class UpdateCheckResultViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: UpdateCheckResultRepository =
        UpdateCheckResultRepository(AppDatabase.getDatabase(app).updateCheckDao)
    
    fun observeAvailableUpdates(owner: LifecycleOwner, observer: (List<UpdateCheckResult>) -> Unit) {
        repository.availableUpdates.observe(owner) { updateCheckResultModels ->
            observer(updateCheckResultModels.map(Converters::toResult))
        }
    }
}