package garden.appl.mitch.database.installation

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import garden.appl.mitch.database.installation.Installation.Companion.DOWNLOAD_OR_INSTALL_ID
import garden.appl.mitch.database.installation.Installation.Companion.GAME_ID
import garden.appl.mitch.database.installation.Installation.Companion.INTERNAL_ID
import garden.appl.mitch.database.installation.Installation.Companion.PACKAGE_NAME
import garden.appl.mitch.database.installation.Installation.Companion.STATUS
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_INSTALLED
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_INSTALLING
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_SUBSCRIPTION
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_WEB_CACHED
import garden.appl.mitch.database.installation.Installation.Companion.TABLE_NAME
import garden.appl.mitch.database.installation.Installation.Companion.UPLOAD_ID

@Dao
abstract class InstallationDao {
    @Query("SELECT * FROM $TABLE_NAME")
    abstract suspend fun getAllKnownInstallationsSync(): List<Installation>

    @Query("""
        SELECT * FROM $TABLE_NAME 
        WHERE $STATUS = $STATUS_INSTALLED
            OR $STATUS = $STATUS_SUBSCRIPTION""")
    abstract suspend fun getFinishedInstallationsAndSubscriptionsSync(): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS = $STATUS_INSTALLED")
    abstract suspend fun getFinishedInstallationsForGame(gameId: Int): List<Installation>

    @Query("""
        SELECT * FROM $TABLE_NAME 
        WHERE $UPLOAD_ID = :uploadId 
            AND $STATUS != $STATUS_INSTALLED 
            AND $STATUS != $STATUS_WEB_CACHED
            AND $STATUS != $STATUS_SUBSCRIPTION""")
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
    abstract suspend fun getPendingInstallationByDownloadId(downloadId: Long): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :installId AND $STATUS = $STATUS_INSTALLING")
    abstract suspend fun getPendingInstallationByInstallId(installId: Long): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS = $STATUS_WEB_CACHED")
    abstract suspend fun getWebInstallationForGame(gameId: Int): Installation?

    @Query("DELETE FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS = $STATUS_WEB_CACHED")
    abstract suspend fun deleteWebInstallationForGame(gameId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $STATUS = $STATUS_WEB_CACHED")
    abstract suspend fun getWebInstallationsSync(): List<Installation>


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