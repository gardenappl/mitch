package ua.gardenapple.itchupdater.database.upload

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.INTERNAL_ID
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.UPLOAD_ID


@Dao
interface UploadDao {
    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId")
    fun getUploadsForGame(gameId: Int): List<Upload>

    @Query("DELETE FROM $TABLE_NAME WHERE $GAME_ID = :gameId")
    fun clearUploadsForGame(gameId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $INTERNAL_ID = :uploadIdInternal LIMIT 1")
    fun getUploadByInternalId(uploadIdInternal: Int): Upload

    @Insert
    fun insert(vararg uploads: Upload)

    @Insert
    fun insert(uploads: List<Upload>)
}