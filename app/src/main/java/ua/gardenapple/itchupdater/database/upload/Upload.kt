package ua.gardenapple.itchupdater.database.upload

import androidx.room.*
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.database.game.Game

/**
 * Represents info about available uploads for any given Game, at the time of installation.
 * The Upload data will then be compared with the server to check for updates.
 *
 * Can also represent a "pending" upload.
 * When a user clicks the download button, the data from the page is saved as "pending" uploads.
 * These may be stored alongside old uploads.
 * Then, once the download/installation is complete, pending uploads become regular uploads.
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
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = INTERNAL_ID)
    var internalId: Int = 0,

    /**
     * Nullable because the upload ID is not always visible when browsing.
     * Will always be non-null for installed games (with the exception of Mitch).
     */
    @ColumnInfo(name = UPLOAD_ID)
    val uploadId: Int?,

    @ColumnInfo(name = IS_PENDING)
    var isPending: Boolean = false,

    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    /**
     * Affects timestamps and version strings.
     */
    @ColumnInfo(name = LOCALE)
    val locale: String = ItchWebsiteParser.UNKNOWN_LOCALE,

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
    val uploadTimestamp: String? = null,

    /**
     * A bitmask of platforms which this upload supports
     */
    @ColumnInfo(name = PLATFORMS)
    val platforms: Int = PLATFORM_NONE
) {
    companion object {
        const val TABLE_NAME = "uploads"

        const val MITCH_RELEASE_NAME = "[Mitch release name]"
        const val MITCH_FILE_SIZE = "[Mitch file size]"
        const val MITCH_UPLOAD_ID = 1_000_000_000

        const val INTERNAL_ID = "internal_id"
        const val UPLOAD_ID = "upload_id"
        const val IS_PENDING = "is_pending"
        const val NAME = "name"
        const val GAME_ID = "game_id"
        const val VERSION = "version"
        const val FILE_SIZE = "file_size"
        const val TIMESTAMP = "timestamp"
        const val LOCALE = "locale"
        const val PLATFORMS = "platforms"

        const val PLATFORM_NONE = 0
        const val PLATFORM_WINDOWS = 1
        const val PLATFORM_MAC = 2
        const val PLATFORM_LINUX = 4
        const val PLATFORM_ANDROID = 8
    }
}