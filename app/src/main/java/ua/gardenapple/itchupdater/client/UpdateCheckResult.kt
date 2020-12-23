package ua.gardenapple.itchupdater.client

import org.jsoup.nodes.Document


data class UpdateCheckResult(
    val code: Int,
    /**
     * Set to null if there is no upload ID or if there are multiple possibilities
     */
    val uploadID: Int? = null,
    val downloadPageUrl: ItchWebsiteParser.DownloadUrl? = null,
    val updateCheckDoc: Document? = null
) {
    companion object {
        const val UP_TO_DATE = 0
        const val UNKNOWN = 1
        const val ACCESS_DENIED = 2
        const val UPDATE_NEEDED = 3
        const val EMPTY = 4
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("{ ")
        sb.append(when (code) {
            UP_TO_DATE -> "Up to date"
            ACCESS_DENIED -> "Access denied"
            UPDATE_NEEDED -> "Update needed"
            EMPTY -> "Empty"
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
        if (updateCheckDoc != null) {
            sb.append(", parsed document available: true")
        }
        sb.append(" }")
        return sb.toString()
    }
}