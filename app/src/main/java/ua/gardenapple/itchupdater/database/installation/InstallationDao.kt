package ua.gardenapple.itchupdater.database.installation

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.TABLE_NAME

@Dao
interface InstallationDao {
    @Query("SELECT * FROM $TABLE_NAME")
    fun getAllInstallations(): LiveData<List<Installation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg installations: Installation)
}