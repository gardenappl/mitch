package ua.gardenapple.itchupdater.database.installation

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.DOWNLOAD_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.INTERNAL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.IS_PENDING
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.upload.Upload

@Dao
interface InstallationDao {
    @Query("SELECT * FROM $TABLE_NAME")
    fun getAllKnownInstallations(): LiveData<List<Installation>>

    @Query("SELECT * FROM $TABLE_NAME WHERE $IS_PENDING = 0")
    fun getAllFinishedInstallations(): LiveData<List<Installation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg installations: Installation)

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $IS_PENDING = 0 LIMIT 1")
    fun findInstallation(gameId: Int): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $IS_PENDING = 1 LIMIT 1")
    fun findPendingInstallation(gameId: Int): Installation?

    @Query("DELETE FROM $TABLE_NAME WHERE $IS_PENDING = 1")
    fun clearPendingInstallations()

    @Query("DELETE FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId")
    fun delete(internalId: Int)

    @Query("DELETE FROM $TABLE_NAME WHERE $GAME_ID = :gameId")
    fun clearAllInstallationsForGame(gameId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_ID = :downloadId LIMIT 1")
    fun findPendingInstallationByDownloadId(downloadId: Long): Installation?
}