package ua.gardenapple.itchupdater.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import ua.gardenapple.itchupdater.client.ItchLibraryItem

class ItchLibraryViewModel(private val repository: ItchLibraryRepository) : ViewModel() {
    
    private lateinit var cachedItemsFlow: Flow<PagingData<ItchLibraryItem>>

    fun getOwnedItems(androidOnly: Boolean) : Flow<PagingData<ItchLibraryItem>> {
        if (!this::cachedItemsFlow.isInitialized)
            cachedItemsFlow = repository.getLibraryStream().cachedIn(viewModelScope)

        if (androidOnly)
            return cachedItemsFlow.map { pagingData -> pagingData.filter { item -> item.isAndroid } }
        else
            return cachedItemsFlow
    }
}