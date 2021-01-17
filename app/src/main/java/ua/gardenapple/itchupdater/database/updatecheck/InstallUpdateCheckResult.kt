package ua.gardenapple.itchupdater.database.updatecheck

import androidx.room.Embedded
import androidx.room.Ignore

class InstallUpdateCheckResult(
    @Embedded
    private val updateCheckResultModel: UpdateCheckResultModel,
    val gameName: String,
    val uploadName: String,
    val currentVersion: String?,
    val packageName: String?,
    val thumbnailUrl: String?,
    val storeUrl: String
){
    @Ignore
    val updateCheckResult = Converters.toResult(updateCheckResultModel)
}