package ua.gardenapple.itchupdater.database.updatecheck

import androidx.lifecycle.LiveData

class UpdateCheckResultRepository(private val updateCheckResultDao: UpdateCheckResultDao) {
    val availableUpdates: LiveData<List<UpdateCheckResultModel>> =
        updateCheckResultDao.getNotUpToDateResults()
}