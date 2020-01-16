package ua.gardenapple.itchupdater.database.installation

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.DOWNLOAD_FINISHED
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.UPLOAD_ID_INTERNAL

@Dao
interface InstallationDao {
    @Query("SELECT * FROM $TABLE_NAME")
    fun getAllKnownInstallations(): LiveData<List<Installation>>

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_FINISHED = 1")
    fun getAllFinishedInstallations(): LiveData<List<Installation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg installations: Installation)

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId LIMIT 1")
    fun findInstallation(gameId: Int): Installation?

    @Query("DELETE FROM $TABLE_NAME WHERE $DOWNLOAD_FINISHED = 0")
    fun clearUnfinishedInstallations()
}