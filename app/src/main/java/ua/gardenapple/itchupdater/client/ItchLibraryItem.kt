package ua.gardenapple.itchupdater.client

data class ItchLibraryItem(
    val thumbnailUrl: String?,
    val title: String,
    val description: String,
    val author: String,
    val downloadUrl: String,
    val purchaseDate: String,
    val isAndroid: Boolean
)