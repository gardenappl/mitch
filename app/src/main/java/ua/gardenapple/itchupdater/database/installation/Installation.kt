package ua.gardenapple.itchupdater.database.installation

import androidx.room.*
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload


@Entity(tableName = Installation.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Upload::class,
            parentColumns = [Upload.INTERNAL_ID],
            childColumns = [Installation.UPLOAD_ID_INTERNAL],
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
        Index(value = [Installation.UPLOAD_ID_INTERNAL])
    ])
data class Installation(
    @PrimaryKey
    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    /**
     * Corresponds to an Upload's uploadId (which should always non-null for files which have actually been downloaded).
     * Mitch has a hardcoded upload ID. (see [MITCH_UPLOAD_ID])
     */
    @ColumnInfo(name = UPLOAD_ID_INTERNAL)
    val uploadIdInternal: Int,

    /**
     * Affects timestamps and version strings.
     */
    @ColumnInfo(name = LOCALE)
    val locale: String = ItchWebsiteParser.UNKNOWN_LOCALE,

    /**
     * Set to null for games where the timestamp is not available.
     */
    @ColumnInfo(name = TIMESTAMP)
    val storeVisitTimestamp: String? = null,

    /**
     * Set to null for downloads which are not installable.
     */
    @ColumnInfo(name = PACKAGE_NAME)
    val packageName: String? = null,

    /**
     * Set to true for games which have been downloaded (and installed, if possible)
     */
    @ColumnInfo(name = DOWNLOAD_FINISHED)
    val downloadFinished: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "installations"

        const val GAME_ID = "game_id"
        const val UPLOAD_ID_INTERNAL = "upload_id"
        const val PACKAGE_NAME = "name"
        const val LOCALE = "locale"
        const val TIMESTAMP = "timestamp"
        const val DOWNLOAD_FINISHED = "download_finished"

        val MITCH_UPLOAD_ID = Upload.calculateInternalId(null, Game.MITCH_GAME_ID, 0)
    }
}