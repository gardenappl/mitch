package ua.gardenapple.itchupdater.database.updatecheck

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.TABLE_NAME

@Dao
abstract class UpdateCheckResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insert(updateCheckResult: UpdateCheckResultModel)

    fun insert(updateCheckResult: UpdateCheckResult) {
        insert(Converters.toModel(updateCheckResult))
    }

    @Query("SELECT * FROM $TABLE_NAME")
    abstract fun getAllUpdateCheckResults(): LiveData<List<UpdateCheckResultModel>>
}