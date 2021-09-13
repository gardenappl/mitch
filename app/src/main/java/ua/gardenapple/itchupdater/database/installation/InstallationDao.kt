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

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS != $STATUS_INSTALLED")
    abstract suspend fun getPendingInstallations(uploadId: Int): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS != $STATUS_INSTALLED")
    abstract suspend fun getPendingInstallation(uploadId: Int): Installation?

    @Query("DELETE FROM $TABLE_NAME WHERE $PACKAGE_NAME = :packageName AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun deleteFinishedInstallation(packageName: String)

    @Query("DELETE FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun deleteFinishedInstallation(uploadId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $PACKAGE_NAME = :packageName AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun getInstallationByPackageName(packageName: String): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS = $STATUS_INSTALLED LIMIT 1")
    abstract suspend fun getFinishedInstallation(uploadId: Int): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId LIMIT 1")
    abstract suspend fun getInstallationById(internalId: Int): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :downloadId AND $STATUS != $STATUS_INSTALLING")
    abstract suspend fun getPendingInstallationByDownloadId(downloadId: Int): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :installId AND $STATUS = $STATUS_INSTALLING")
    abstract suspend fun getPendingInstallationByInstallId(installId: Long): Installation?


    @Query("DELETE FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId")
    abstract suspend fun delete(internalId: Int)

    @Delete
    abstract suspend fun delete(installation: Installation)

    @Delete
    abstract suspend fun delete(installations: List<Installation>)


    @Update
    abstract suspend fun update(installation: Installation)

    @Update
    abstract suspend fun update(installations: List<Installation>)

    /**
     * @return -1 if an install with the same [Installation.internalId] already exists (no insertion)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(installation: Installation): Long

    @Transaction
    open suspend fun upsert(install: Installation) {
        val id = insert(install)
        if (id == -1L) {
            update(install)
        }
    }
}