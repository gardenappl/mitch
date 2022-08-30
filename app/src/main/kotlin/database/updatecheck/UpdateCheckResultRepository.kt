package garden.appl.mitch.database.updatecheck

import androidx.lifecycle.LiveData

class UpdateCheckResultRepository(updateCheckResultDao: UpdateCheckResultDao) {
    val availableUpdates: LiveData<List<InstallUpdateCheckResult>> =
        updateCheckResultDao.getNotUpToDateResults()
}