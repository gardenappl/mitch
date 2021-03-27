package ua.gardenapple.itchupdater.client

import java.util.*

data class ItchLibraryItem(
    val thumbnailUrl: String,
    val title: String,
    val description: String,
    val author: String,
    val downloadUrl: String,
    //Null if the purchase date is the same as previous item
    val purchaseDate: Date?,
    val isAndroid: Boolean
)