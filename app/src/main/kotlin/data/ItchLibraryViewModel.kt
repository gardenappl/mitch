package garden.appl.mitch.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import garden.appl.mitch.client.ItchLibraryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ItchLibraryViewModel(private val repository: ItchLibraryRepository) : ViewModel() {
    
    private lateinit var cachedItemsFlow: Flow<PagingData<ItchLibraryItem>>

    fun getOwnedItems(searchString: String, androidOnly: Boolean) : Flow<PagingData<ItchLibraryUiModel>> {
        if (!this::cachedItemsFlow.isInitialized)
            cachedItemsFlow = repository.getLibraryStream().cachedIn(viewModelScope)

        val itemsFlow = if (androidOnly) {
            cachedItemsFlow.map { pagingData ->
                pagingData.filter { item -> item.isAndroid }
            }
        } else {
            cachedItemsFlow
        }.map { pagingData ->
            pagingData.filter { item -> item.title.contains(searchString, ignoreCase = true) }
        }

        return itemsFlow
            .map { pagingData -> pagingData.map { item -> ItchLibraryUiModel.Item(item) } }
            .map { 
                it.insertSeparators { before, after ->
                    if (after == null)
                        return@insertSeparators null

                    if (before == null) {
                        return@insertSeparators ItchLibraryUiModel.Separator(
                            after.item.purchaseDate, 
                            isFirst = true
                        )
                    }

                    if (before.item.purchaseDate != after.item.purchaseDate) {
                        return@insertSeparators ItchLibraryUiModel.Separator(
                            after.item.purchaseDate,
                            isFirst = false
                        )
                    }

                    null
                }
            }
    }
}