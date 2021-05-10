package ua.gardenapple.itchupdater.client

import ua.gardenapple.itchupdater.database.installation.Installation


data class UpdateCheckResult(
    val installationId: Int,
    val code: Int,
    /**
     * Set to null if there is no upload ID or if there are multiple possibilities
     */
    val uploadID: Int? = null,
    /**
     * Only used if [code] == [UPDATE_AVAILABLE]
     */
    val downloadPageUrl: ItchWebsiteParser.DownloadUrl? = null,

    val newUploadName: String? = null,
    val newVersionString: String? = null,
    val newSize: String? = null,
    val newTimestamp: String? = null,

    /**
     * Set to null unless [code] is equal to [UpdateCheckResult.ERROR]
     */
    val errorReport: String? = null,
    
    /**
     * Used for UI
     */
    var isInstalling: Boolean = false
) {
    companion object {
        const val UP_TO_DATE = 0
//        const val UNKNOWN = 1
        const val ACCESS_DENIED = 2
        const val UPDATE_AVAILABLE = 3
        const val EMPTY = 4
        const val ERROR = 5
    }

    /**
     * Construct an [UpdateCheckResult] with code [UPDATE_AVAILABLE], based on a new
     * available [Installation].
     */
    constructor(
        currentInstallId: Int,
        downloadPageUrl: ItchWebsiteParser.DownloadUrl,
        availableUpdateInstall: Installation?
    ) : this(
        installationId = currentInstallId,
        code = UPDATE_AVAILABLE,
        uploadID = availableUpdateInstall?.uploadId,
        downloadPageUrl = downloadPageUrl,
        newUploadName = availableUpdateInstall?.uploadName,
        newVersionString = availableUpdateInstall?.version,
        newSize = availableUpdateInstall?.fileSize,
        newTimestamp = availableUpdateInstall?.uploadTimestamp
    )
}