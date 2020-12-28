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

    @Query("SELECT * FROM $TABLE_NAME WHERE $STATUS = $STATUS_INSTALLED")
    abstract fun getFinishedInstallations(): LiveData<List<Installation>>

    //for debug purposes
    @Query("SELECT * FROM $TABLE_NAME")
    abstract fun getAllInstallationsSync(): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $STATUS = $STATUS_INSTALLED")
    abstract fun getFinishedInstallationsSync(): List<Installation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(vararg installations: Installation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(installations: List<Installation>)

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS = $STATUS_INSTALLED")
    abstract fun getInstallations(gameId: Int): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $STATUS != $STATUS_INSTALLED")
    abstract fun getPendingInstallations(gameId: Int): List<Installation>

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS != $STATUS_INSTALLED LIMIT 1")
    abstract fun getPendingInstallation(uploadId: Int): Installation?

    @Query("DELETE FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS != $STATUS_INSTALLED")
    abstract fun deletePendingInstallation(uploadId: Int)

    @Update
    abstract fun update(vararg installations: Installation)

    @Query("DELETE FROM $TABLE_NAME WHERE $INTERNAL_ID = :internalId")
    abstract fun delete(internalId: Int)

    @Delete
    abstract fun delete(installation: Installation)

    @Query("DELETE FROM $TABLE_NAME WHERE $PACKAGE_NAME = :packageName AND $STATUS = $STATUS_INSTALLED")
    abstract fun deleteFinishedInstallation(packageName: String)

    @Query("DELETE FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $STATUS = $STATUS_INSTALLED")
    abstract fun deleteFinishedInstallation(uploadId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $PACKAGE_NAME = :packageName AND $STATUS = $STATUS_INSTALLED LIMIT 1")
    abstract fun getInstallationByPackageName(packageName: String): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :downloadId AND $STATUS != $STATUS_INSTALLING LIMIT 1")
    abstract fun findPendingInstallationByDownloadId(downloadId: Long): Installation?

    @Query("SELECT * FROM $TABLE_NAME WHERE $DOWNLOAD_OR_INSTALL_ID = :installSessionId AND $STATUS = $STATUS_INSTALLING LIMIT 1")
    abstract fun findPendingInstallationBySessionId(installSessionId: Int): Installation?
}