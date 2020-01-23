package ua.gardenapple.itchupdater.database.installation

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.DOWNLOAD_OR_INSTALL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.INTERNAL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_DOWNLOADING
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_INSTALLED
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_INSTALLING
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.TABLE_NAME

@Dao
interface InstallationDao {
    @Query("SELECT * FROM $TABLE_NAME")
    fun getAllKnownInstallations(): LiveData<List<Installation>>

    @Query("SELECT * FROM $TABLE_NAME WHERE $STATUS = 0")
    fun getAllFinishedInstallations(): LiveData<List<Installation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg installations: Installation)

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS = $STATUS_INSTALLED LIMIT 1")
    fun findInstallation(gameId: Int): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS != $STATUS_INSTALLED LIMIT 1")
    fun findPendingInstallation(gameId: Int): Installation?

//    @Query("DELETE FROM $TABLE_NAME WHERE $STATUS != $STATUS_INSTALLED")
//    fun clearPendingInstallations()

    @Update
    fun update(vararg installations: Installation)

    @Query("DELETE FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId")
    fun delete(internalId: Int)

    @Delete
    fun delete(installation: Installation)

    @Query("DELETE FROM $TABLE_NAME WHERE $GAME_ID = :gameId")
    fun clearAllInstallationsForGame(gameId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :downloadId AND $STATUS = $STATUS_DOWNLOADING LIMIT 1")
    fun findPendingInstallationByDownloadId(downloadId: Long): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :installSessionId AND $STATUS = $STATUS_INSTALLING LIMIT 1")
    fun findPendingInstallationBySessionId(installSessionId: Int): Installation?
}