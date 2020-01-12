package ua.gardenapple.itchupdater.database.upload

import androidx.room.*
import ua.gardenapple.itchupdater.database.game.Game


@Entity(tableName = Upload.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = [Game.GAME_ID],
            childColumns = [Upload.GAME_ID]
        )
    ],
    indices = [
        Index(value = [Upload.UPLOAD_ID]),
        Index(value = [Upload.GAME_ID])
    ])
data class Upload(
    /**
     * Since we don't always have an uploadId, and we need to have a primary key, we use this workaround.
     * If there's an uploadId, we use that as the primary key (and it's always >0)
     * If there's no uploadId, we set internalId to a negative integer, derived from the gameId and uploadNum.
     */
    @PrimaryKey
    val internalId: Int,

    @ColumnInfo(name = UPLOAD_ID)
    val uploadId: Int?,

    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    @ColumnInfo(name = VERSION)
    val version: String?,

    @ColumnInfo(name = NAME)
    val name: String,

    @ColumnInfo(name = FILE_SIZE)
    val fileSize: String,

    @ColumnInfo(name = TIMESTAMP)
    val uploadTimestamp: String?
) {

    constructor(uploadId: Int?, gameId: Int, uploadNum: Int, version: String?,
                name: String, fileSize: String, uploadTimestamp: String?)
            : this(uploadId ?: gameId * 100 + uploadNum,
                   uploadId, gameId, version, name, fileSize, uploadTimestamp)

    companion object {
        const val TABLE_NAME = "uploads"
        const val UPLOAD_ID = "upload_id"
        const val NAME = "name"
        const val GAME_ID = "game_id"
        const val VERSION = "version"
        const val FILE_SIZE = "file_size"
        const val TIMESTAMP = "timestamp"
    }
}