package garden.appl.mitch.data

import garden.appl.mitch.client.ItchLibraryItem

sealed class ItchLibraryUiModel {
    data class Item(val item: ItchLibraryItem) : ItchLibraryUiModel()
    data class Separator(val purchaseDate: String, val isFirst: Boolean) : ItchLibraryUiModel()
}