package ua.gardenapple.itchupdater.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import ua.gardenapple.itchupdater.client.ItchLibraryItem
import ua.gardenapple.itchupdater.client.ItchLibraryParser

class ItchLibraryRepository {
    private val itchLibraryPagingSource = ItchLibraryPagingSource()
    
    fun getLibraryStream(): Flow<PagingData<ItchLibraryItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = ItchLibraryParser.PAGE_SIZE,
                enablePlaceholders = false,
                initialLoadSize = ItchLibraryParser.PAGE_SIZE
            ),
            pagingSourceFactory = { itchLibraryPagingSource }
        ).flow
    }
}