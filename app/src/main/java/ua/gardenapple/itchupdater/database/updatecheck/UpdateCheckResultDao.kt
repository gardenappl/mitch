package ua.gardenapple.itchupdater.database.updatecheck

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.UpdateCheckResult.Companion.UPDATE_AVAILABLE
import ua.gardenapple.itchupdater.client.UpdateCheckResult.Companion.UP_TO_DATE
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.CODE
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.INSTALLATION_ID
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.TABLE_NAME

@Dao
abstract class UpdateCheckResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insert(updateCheckResult: UpdateCheckResultModel)

    fun insert(updateCheckResult: UpdateCheckResult) {
        insert(Converters.toModel(updateCheckResult))
    }

    @Query("SELECT * FROM $TABLE_NAME WHERE $CODE != $UP_TO_DATE")
    abstract fun getNotUpToDateResults(): LiveData<List<UpdateCheckResultModel>>

    @Query("SELECT * FROM $TABLE_NAME WHERE $CODE != $UP_TO_DATE")
    abstract fun getNotUpToDateResultsSync(): List<UpdateCheckResultModel>
    
    @Query("SELECT * FROM $TABLE_NAME WHERE $INSTALLATION_ID = :installId")
    protected abstract fun getUpdateCheckResultModelForInstall(installId: Int): UpdateCheckResultModel?

    fun getUpdateCheckResultForInstall(installId: Int): UpdateCheckResult? {
        return getUpdateCheckResultModelForInstall(installId)?.let { Converters.toResult(it) }
    }
}