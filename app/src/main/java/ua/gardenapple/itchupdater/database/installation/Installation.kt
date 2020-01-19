package ua.gardenapple.itchupdater.database.installation

import androidx.room.*
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload


/**
 * Represents info about a downloaded or installed game.
 *
 * Can also represent a "pending" installation.
 * When a user clicks the download button, the data from the page is saved as a "pending" Installation.
 * It may be stored alongside the old Installation.
 * Then, once the download/installation is complete, the pending Installation becomes a regular Installation.
 */
@Entity(tableName = Installation.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Upload::class,
            parentColumns = [Upload.UPLOAD_ID],
            childColumns = [Installation.UPLOAD_ID],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Game::class,
            parentColumns = [Game.GAME_ID],
            childColumns = [Installation.GAME_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = [Installation.GAME_ID]),
        Index(value = [Installation.UPLOAD_ID])
    ])
data class Installation(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = INTERNAL_ID)
    var internalId: Int = 0,

    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    /**
     * Corresponds to an Upload's uploadId (which should always non-null for files which have actually been downloaded).
     * Mitch has a hardcoded upload ID. (see [MITCH_UPLOAD_ID])
     */
    @ColumnInfo(name = UPLOAD_ID)
    val uploadId: Int,

    /**
     * Set to null for downloads which are not installable.
     */
    @ColumnInfo(name = PACKAGE_NAME)
    val packageName: String? = null,

    @ColumnInfo(name = IS_PENDING)
    var isPending: Boolean = false,

    @ColumnInfo(name = DOWNLOAD_ID)
    var downloadId: Long? = null
) {
    companion object {
        const val TABLE_NAME = "installations"

        const val INTERNAL_ID = "internal_id"
        const val GAME_ID = "game_id"
        const val UPLOAD_ID = "upload_id"
        const val PACKAGE_NAME = "package_name"
        const val DOWNLOAD_ID = "download_id"
        const val IS_PENDING = "is_pending"
    }
}