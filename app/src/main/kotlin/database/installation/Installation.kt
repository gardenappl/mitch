package garden.appl.mitch.database.installation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_DOWNLOADING
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_INSTALLED
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_INSTALLING
import garden.appl.mitch.database.installation.Installation.Companion.STATUS_READY_TO_INSTALL
import garden.appl.mitch.install.NativeInstaller
import garden.appl.mitch.install.SessionInstaller


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
     * There should only ever be a single Installation
     * with a particular uploadId and with [STATUS_INSTALLED].
     * (and, possibly, another Installation with the same uploadId
     * with a pending status)
     *
     * Mitch has a hardcoded upload ID. (see [Installation.MITCH_UPLOAD_ID])
     * Web games all share a hardcoded upload ID. (see [Installation.WEB_UPLOAD_ID])
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
     * If [STATUS_INSTALLED]:
     *   value is null.
     * Else if value fits within an Int:
     *   value is the sessionId from the [SessionInstaller], we are downloading an APK
     *   directly into an install session.
     * Else if [STATUS_DOWNLOADING] or [STATUS_READY_TO_INSTALL]:
     *   value is a unique "download ID" assigned by Mitch; we are downloading either a generic file,
     *   or maybe an APK for the [NativeInstaller].
     * Else if [STATUS_INSTALLING]:
     *   value is a unique "install ID" assigned by Mitch, we are installing an APK
     *   with the [NativeInstaller].
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

        const val WEB_UPLOAD_NAME = "[Web release name]"
        const val WEB_FILE_SIZE = "[Web file size]"
        const val WEB_UPLOAD_ID = 2_000_000_000

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
        const val STATUS_WEB_CACHED = 4

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