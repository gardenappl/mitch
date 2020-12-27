package ua.gardenapple.itchupdater.client

import java.util.*


data class UpdateCheckResult(
    val installationId: Int,
    val code: Int,
    /**
     * Set to null if there is no upload ID or if there are multiple possibilities
     */
    val uploadID: Int? = null,
    val downloadPageUrl: ItchWebsiteParser.DownloadUrl? = null,

    val newVersionString: String? = null,
    val newSize: String? = null,
    val newTimestamp: String? = null,

    /**
     * Set to null unless [code] is equal to [UpdateCheckResult.ERROR]
     */
    val errorReport: String? = null
) {
    companion object {
        const val UP_TO_DATE = 0
        const val UNKNOWN = 1
        const val ACCESS_DENIED = 2
        const val UPDATE_NEEDED = 3
        const val EMPTY = 4
        const val ERROR = 5
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("{ ")
        sb.append(when (code) {
            UP_TO_DATE -> "Up to date"
            ACCESS_DENIED -> "Access denied"
            UPDATE_NEEDED -> "Update needed"
            EMPTY -> "Empty"
            ERROR -> "Error"
            else -> "Unknown"
        })
        if (uploadID != null) {
            sb.append(", new upload ID: ")
            sb.append(uploadID)
        }
        if (downloadPageUrl != null) {
            sb.append(", download page: ")
            sb.append(downloadPageUrl)
        }
        sb.append(" }")
        return sb.toString()
    }
}