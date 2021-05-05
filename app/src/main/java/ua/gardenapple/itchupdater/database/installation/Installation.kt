package ua.gardenapple.itchupdater.database.installation

import androidx.room.*
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_DOWNLOADING
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_INSTALLED
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_INSTALLING
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS_READY_TO_INSTALL
import ua.gardenapple.itchupdater.files.DownloaderWorker
import ua.gardenapple.itchupdater.files.DownloaderFetch


/**
 * Represents info about a downloaded or installed game.
 *
 * Can also represent a "pending" installation.
 * When a user clicks the download button, the data from the page is saved as a "pending" Installation.
 * It may be stored alongside the old Installation.
 * Then, once the download/installation is complete, the pending Installation becomes a regular Installation.
 * Also, any Installations on this device which are not in [availableUploadIds] get deleted
 */
@Entity(tableName = Installation.TABLE_NAME,
    foreignKeys = [
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
    var internalId: Int,

    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    /**
     * Corresponds to an Upload's uploadId (which should always be non-null for files which have actually been downloaded).
     * Mitch has a hardcoded upload ID. (see [Installation.MITCH_UPLOAD_ID])
     */
    @ColumnInfo(name = UPLOAD_ID)
    val uploadId: Int,
    
    /**
     * Encoded as a string for the purposes of SQL, please don't use this constructor directly.
     *
     * Array of other upload Ids which are available alongside this install.
     * Any local installs whose uploadIds are not in this array will be deleted.
     * If set to null, only install with same [uploadId] is deleted
     */
    @ColumnInfo(name = AVAILABLE_UPLOAD_IDS)
    val availableUploadIdsString: String?,

    /**
     * Set to null for downloads which are not installable.
     */
    @ColumnInfo(name = PACKAGE_NAME)
    val packageName: String?,

    @ColumnInfo(name = STATUS)
    var status: Int,

    /**
     * null if [STATUS_INSTALLED]
     * downloadId if [STATUS_DOWNLOADING] or [STATUS_READY_TO_INSTALL]
     * installId if [STATUS_INSTALLING]
     *
     * If download ID fits within an Int then it's handled by [DownloaderFetch],
     * otherwise, it's handled by [DownloaderWorker].
     */
    @ColumnInfo(name = DOWNLOAD_OR_INSTALL_ID)
    var downloadOrInstallId: Long?,

    /**
     * Affects timestamps and version strings.
     */
    @ColumnInfo(name = LOCALE)
    val locale: String,

    /**
     * Nullable because the version string is not available for some projects.
     */
    @ColumnInfo(name = VERSION)
    val version: String?,

    @ColumnInfo(name = UPLOAD_NAME)
    val uploadName: String,

    /**
     * When checking updates, we might check if the file size has changed.
     */
    @ColumnInfo(name = FILE_SIZE)
    val fileSize: String,

    /**
     * Nullable because the build timestamp is not available for some projects.
     */
    @ColumnInfo(name = TIMESTAMP)
    val uploadTimestamp: String?,

    /**
     * A bitmask of platforms which this upload supports
     */
    @ColumnInfo(name = PLATFORMS)
    val platforms: Int,

    /**
     * Path to file in public Downloads/ folder, if it has been moved there
     */
    @ColumnInfo(name = EXTERNAL_FILE_NAME)
    val externalFileName: String?
) {
    constructor(
        internalId: Int = 0,
        gameId: Int,
        uploadId: Int,
        availableUploadIds: List<Int>?,
        packageName: String? = null,
        status: Int = STATUS_INSTALLED,
        downloadOrInstallId: Long? = null,
        locale: String = ItchWebsiteParser.UNKNOWN_LOCALE,
        version: String? = null,
        uploadName: String,
        fileSize: String,
        uploadTimestamp: String? = null,
        platforms: Int = PLATFORM_NONE,
        externalFileName: String? = null
    ) : this(
        internalId = internalId,
        gameId = gameId,
        uploadId = uploadId,
        availableUploadIdsString = availableUploadIds?.joinToString(separator = ","),
        packageName = packageName,
        status = status,
        downloadOrInstallId = downloadOrInstallId,
        locale = locale,
        version = version,
        uploadName = uploadName,
        fileSize = fileSize,
        uploadTimestamp = uploadTimestamp,
        platforms = platforms,
        externalFileName = externalFileName
    )

    companion object {
        const val TABLE_NAME = "installations"

        const val MITCH_UPLOAD_NAME = "[Mitch release name]"
        const val MITCH_FILE_SIZE = "[Mitch file size]"
        const val MITCH_UPLOAD_ID = 1_000_000_000

        const val INTERNAL_ID = "internal_id"
        const val GAME_ID = "game_id"
        const val UPLOAD_ID = "upload_id"
        const val PACKAGE_NAME = "package_name"
        const val DOWNLOAD_OR_INSTALL_ID = "download_id"
        const val STATUS = "is_pending"
        const val UPLOAD_NAME = "name"
        const val VERSION = "version"
        const val FILE_SIZE = "file_size"
        const val TIMESTAMP = "timestamp"
        const val LOCALE = "locale"
        const val PLATFORMS = "platforms"
        const val EXTERNAL_FILE_NAME = "external_file_name"
        const val AVAILABLE_UPLOAD_IDS = "available_uploads"

        const val STATUS_INSTALLED = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_INSTALLING = 2
        const val STATUS_READY_TO_INSTALL = 3

        const val PLATFORM_NONE = 0
        const val PLATFORM_WINDOWS = 1
        const val PLATFORM_MAC = 2
        const val PLATFORM_LINUX = 4
        const val PLATFORM_ANDROID = 8
    }


    @Ignore
    val availableUploadIds: List<Int>? =
        availableUploadIdsString?.split(',')?.map { uploadIdString ->
            Integer.parseInt(uploadIdString)
        }
}