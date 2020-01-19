package ua.gardenapple.itchupdater.database.upload

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.INTERNAL_ID
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.IS_PENDING
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.upload.Upload.Companion.UPLOAD_ID


@Dao
interface UploadDao {
    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $IS_PENDING = 0")
    fun getUploadsForGame(gameId: Int): List<Upload>

    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $IS_PENDING = 1")
    fun getPendingUploadsForGame(gameId: Int): List<Upload>

    @Query("DELETE FROM $TABLE_NAME WHERE $GAME_ID = :gameId")
    fun clearAllUploadsForGame(gameId: Int)

    @Query("DELETE FROM $TABLE_NAME WHERE $GAME_ID = :gameId AND $IS_PENDING = 1")
    fun clearPendingUploadsForGame(gameId: Int)

    @Query("SELECT * FROM $TABLE_NAME WHERE $UPLOAD_ID = :uploadId AND $IS_PENDING = 0 LIMIT 1")
    fun getUploadById(uploadId: Int): Upload?

    @Insert
    fun insert(vararg uploads: Upload)

    @Insert
    fun insert(uploads: List<Upload>)
}