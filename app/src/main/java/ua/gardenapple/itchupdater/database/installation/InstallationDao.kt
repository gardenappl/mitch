package ua.gardenapple.itchupdater.database.installation

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.DOWNLOAD_OR_INSTALL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.INTERNAL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.PACKAGE_NAME
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_INSTALLED
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_INSTALLING
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.UPLOAD_ID

@Dao
abstract class InstallationDao {
    @Query("SELECT * FROM $TABLE_NAME")
    abstract fun getAllKnownInstallations(): LiveData<List<Installation>>

    @Query("SELECT * FROM $TABLE_NAME")
    abstract suspend fun getAllKnownInstallationsSync(): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $STATUS = $STATUS_INSTALLED")
    abstract fun getFinishedInstallations(): LiveData<List<Installation>>

    @Query("SELECT * FROM $TABLE_NAME WHERE $STATUS = $STATUS_INSTALLED")
    abstract suspend fun getFinishedInstallationsSync(): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun getFinishedInstallationsForGame(gameId: Int): List<Installation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(installation: Installation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(installations: List<Installation>)

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS != $STATUS_INSTALLED")
    abstract suspend fun getPendingInstallations(uploadId: Int): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS != $STATUS_INSTALLED")
    abstract suspend fun getPendingInstallation(uploadId: Int): Installation?

    @Update
    abstract suspend fun update(vararg installations: Installation)

    @Update
    abstract suspend fun update(installations: List<Installation>)

    @Query("DELETE FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId")
    abstract suspend fun delete(internalId: Int)

    @Delete
    abstract suspend fun delete(installation: Installation)

    @Delete
    abstract suspend fun delete(installations: List<Installation>)

    @Query("DELETE FROM $TABLE_NAME WHERE $PACKAGE_NAME = :packageName AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun deleteFinishedInstallation(packageName: String)

    @Query("DELETE FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun deleteFinishedInstallation(uploadId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $PACKAGE_NAME = :packageName AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun getInstallationByPackageName(packageName: String): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId LIMIT 1")
    abstract suspend fun getInstallationById(internalId: Int): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :downloadId AND $STATUS != $STATUS_INSTALLING")
    abstract suspend fun getPendingInstallationByDownloadId(downloadId: Long): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :installSessionId AND $STATUS = $STATUS_INSTALLING")
    abstract suspend fun getPendingInstallationBySessionId(installSessionId: Int): Installation?
}