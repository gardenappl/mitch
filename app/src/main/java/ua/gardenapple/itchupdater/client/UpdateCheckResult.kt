package ua.gardenapple.itchupdater.client


data class UpdateCheckResult(
    val code: Int,
    /**
     * Set to null if there is no upload ID or if there are multiple possibilities
     */
    val uploadID: Int? = null
) {
    companion object {
        const val UP_TO_DATE = 0
        const val UNKNOWN = 1
        const val ACCESS_DENIED = 2
        const val UPDATE_NEEDED = 3
        const val EMPTY = 4
    }
}