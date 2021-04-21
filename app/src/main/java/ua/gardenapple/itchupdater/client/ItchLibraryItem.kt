package ua.gardenapple.itchupdater.client

data class ItchLibraryItem(
    val thumbnailUrl: String?,
    val title: String,
    val description: String,
    val author: String,
    val downloadUrl: String,
    //Null means the purchase date is the same as previous item
    //(but not necessarily the other way around)
    val purchaseDate: String?,
    val isAndroid: Boolean
)