package ua.gardenapple.itchupdater.database.updatecheck

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.UpdateCheckResult.Companion.UP_TO_DATE
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.CODE
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.INSTALLATION_ID
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel.Companion.UPLOAD_ID

@Dao
abstract class UpdateCheckResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(updateCheckResult: UpdateCheckResultModel)

    suspend fun insert(updateCheckResult: UpdateCheckResult) {
        insert(Converters.toModel(updateCheckResult))
    }

    @Query("""
        SELECT update_check_results.*,
            installations.${Installation.VERSION} as currentVersion,
            installations.${Installation.PACKAGE_NAME} as packageName,
            games.${Game.NAME} as gameName,
            games.${Game.THUMBNAIL_URL} as thumbnailUrl,
            games.${Game.STORE_URL} as storeUrl
        FROM update_check_results
        INNER JOIN installations ON update_check_results.$INSTALLATION_ID = installations.${Installation.INTERNAL_ID}
        INNER JOIN games ON installations.${Installation.GAME_ID} = games.${Game.GAME_ID}
        WHERE $CODE != $UP_TO_DATE""")
    abstract fun getNotUpToDateResults(): LiveData<List<InstallUpdateCheckResult>>
    
    @Query("SELECT * FROM $TABLE_NAME WHERE $INSTALLATION_ID = :installId")
    protected abstract suspend fun getUpdateCheckResultModel(installId: Int): UpdateCheckResultModel?

    suspend fun getUpdateCheckResult(installId: Int): UpdateCheckResult? {
        return getUpdateCheckResultModel(installId)?.let { Converters.toResult(it) }
    }
    
    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId")
    protected abstract suspend fun getUpdateCheckResultModelForUpload(uploadId: Int): UpdateCheckResultModel?

    suspend fun getUpdateCheckResultForUpload(uploadId: Int): UpdateCheckResult? {
        return getUpdateCheckResultModelForUpload(uploadId)?.let { Converters.toResult(it) }
    }
}