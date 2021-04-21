package ua.gardenapple.itchupdater.data

import ua.gardenapple.itchupdater.client.ItchLibraryItem

sealed class ItchLibraryUiModel {
    data class Item(val item: ItchLibraryItem) : ItchLibraryUiModel()
    data class Separator(val purchaseDate: String, val isFirst: Boolean) : ItchLibraryUiModel()
}