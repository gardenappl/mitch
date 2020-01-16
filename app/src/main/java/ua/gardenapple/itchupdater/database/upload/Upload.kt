package ua.gardenapple.itchupdater.database.upload

import androidx.room.*
import ua.gardenapple.itchupdater.database.game.Game

/**
 * Represents info about available uploads for any given Game, at the time of installation.
 * Should be written to the database after downloading a file.
 */
@Entity(tableName = Upload.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = [Game.GAME_ID],
            childColumns = [Upload.GAME_ID],
            onDelete = ForeignKey.CASCADE
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
    @ColumnInfo(name = INTERNAL_ID)
    val internalId: Int,

    /**
     * Nullable because the upload ID is not always visible when browsing.
     * (however, it will always be non-null for installed games)
     */
    @ColumnInfo(name = UPLOAD_ID)
    val uploadId: Int?,

    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    /**
     * Nullable because the version string is not available for some projects.
     */
    @ColumnInfo(name = VERSION)
    val version: String? = null,

    @ColumnInfo(name = NAME)
    val name: String,

    @ColumnInfo(name = FILE_SIZE)
    val fileSize: String,

    /**
     * Nullable because the build timestamp is not available for some projects.
     */
    @ColumnInfo(name = TIMESTAMP)
    val uploadTimestamp: String? = null
) {

    constructor(
        uploadId: Int?,
        gameId: Int,
        uploadNum: Int,
        version: String?,
        name: String,
        fileSize: String,
        uploadTimestamp: String? = null
    )
            : this(calculateInternalId(uploadId, gameId, uploadNum), uploadId, gameId, version,
                   name, fileSize, uploadTimestamp)

    companion object {
        const val TABLE_NAME = "uploads"

        const val INTERNAL_ID = "internalId"
        const val UPLOAD_ID = "upload_id"
        const val NAME = "name"
        const val GAME_ID = "game_id"
        const val VERSION = "version"
        const val FILE_SIZE = "file_size"
        const val TIMESTAMP = "timestamp"

        fun calculateInternalId(uploadId: Int?, gameId: Int, uploadNum: Int): Int {
            return uploadId ?: -(gameId * 100 + uploadNum)
        }
    }
}