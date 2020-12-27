package ua.gardenapple.itchupdater.database.updatecheck

import androidx.room.*
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.upload.Upload
import java.util.*

@Entity(tableName = UpdateCheckResultModel.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Installation::class,
            parentColumns = [Installation.INTERNAL_ID],
            childColumns = [UpdateCheckResultModel.INSTALLATION_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = [UpdateCheckResultModel.INSTALLATION_ID])
    ])
data class UpdateCheckResultModel(
    @PrimaryKey
    @ColumnInfo(name = INSTALLATION_ID)
    val installId: Int,
    
    @ColumnInfo(name = CODE)
    val code: Int,
    
    @ColumnInfo(name = TIMESTAMP)
    val timestamp: String?,
    
    @ColumnInfo(name = VERSION)
    val versionString: String?,
    
    @ColumnInfo(name = FILE_SIZE)
    val fileSize: String?,

    @ColumnInfo(name = UPLOAD_ID)
    val uploadID: Int? = null,

    @ColumnInfo(name = DOWNLOAD_URL)
    val downloadPageUrl: String? = null,

    @ColumnInfo(name = DOWNLOAD_IS_STORE_PAGE)
    val downloadPageIsStorePage: Boolean = false,

    @ColumnInfo(name = DOWNLOAD_IS_PERMANENT)
    val downloadPageIsPermanent: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "update_check_results"
        
        const val INSTALLATION_ID = "install_id"
        const val CODE = "code"
        const val TIMESTAMP = "timestamp"
        const val VERSION = "version"
        const val FILE_SIZE = "file_size"
        const val UPLOAD_ID = "upload_id"
        const val DOWNLOAD_URL = "download_url"
        const val DOWNLOAD_IS_STORE_PAGE = "download_is_store_page"
        const val DOWNLOAD_IS_PERMANENT = "download_is_permanent"
    }
}

class Converters {
    companion object {
        @TypeConverter
        @JvmStatic
        fun toResult(model: UpdateCheckResultModel): UpdateCheckResult {
            return UpdateCheckResult(
                installationId = model.installId,
                code = model.code,
                newTimestamp = model.timestamp,
                newSize = model.fileSize,
                newVersionString = model.versionString,
                uploadID = model.uploadID,
                downloadPageUrl = model.downloadPageUrl?.let {
                    ItchWebsiteParser.DownloadUrl(
                        it, model.downloadPageIsPermanent, model.downloadPageIsStorePage
                    )
                }
            )
        }

        @TypeConverter
        @JvmStatic
        fun toModel(result: UpdateCheckResult): UpdateCheckResultModel {
            return UpdateCheckResultModel(
                installId = result.installationId,
                code = result.code,
                timestamp = result.newTimestamp,
                versionString = result.newVersionString,
                fileSize = result.newSize,
                uploadID = result.uploadID,
                downloadPageUrl = result.downloadPageUrl?.url,
                downloadPageIsStorePage = result.downloadPageUrl?.isStorePage == true,
                downloadPageIsPermanent = result.downloadPageUrl?.isPermanent == true
            )
        }
    }
}