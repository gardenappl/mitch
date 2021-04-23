package ua.gardenapple.itchupdater.client

data class ItchLibraryItem(
    val thumbnailUrl: String?,
    val title: String,
    val description: String,
    val author: String,
    val downloadUrl: String,
    /**
     * Can be null if this is the first item in a page,
     * in which case use the purchaseDate from the last item in the previous page
     */
    val purchaseDate: String?,
    val isAndroid: Boolean
)