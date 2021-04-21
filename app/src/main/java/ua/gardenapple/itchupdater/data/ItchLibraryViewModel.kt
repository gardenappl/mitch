package ua.gardenapple.itchupdater.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.gardenapple.itchupdater.client.ItchLibraryItem

class ItchLibraryViewModel(private val repository: ItchLibraryRepository) : ViewModel() {
    
    private lateinit var cachedItemsFlow: Flow<PagingData<ItchLibraryItem>>

    fun getOwnedItems(androidOnly: Boolean) : Flow<PagingData<ItchLibraryUiModel>> {
        if (!this::cachedItemsFlow.isInitialized)
            cachedItemsFlow = repository.getLibraryStream().cachedIn(viewModelScope)

        val itemsFlow = if (androidOnly)
            cachedItemsFlow.map { pagingData -> pagingData.filter { item -> item.isAndroid } }
        else
            cachedItemsFlow

        var lastDate: String? = null

        return itemsFlow
            .map { pagingData -> pagingData.map { item -> ItchLibraryUiModel.Item(item) } }
            .map { 
                it.insertSeparators { _, after ->
                    Log.d("agag", "lastDate: $lastDate, item: ${after?.item}")
                    if (after == null)
                        return@insertSeparators null

                    if (lastDate == null) {
                        lastDate = after.item.purchaseDate
                        return@insertSeparators ItchLibraryUiModel.Separator(lastDate!!, true)
                    }

                    if (after.item.purchaseDate != null && after.item.purchaseDate != lastDate) {
                        lastDate = after.item.purchaseDate
                        return@insertSeparators ItchLibraryUiModel.Separator(lastDate!!, false)
                    }

                    null
                }
            }
    }
}