package garden.appl.mitch.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import garden.appl.mitch.client.ItchLibraryItem
import garden.appl.mitch.client.ItchLibraryParser
import kotlinx.coroutines.flow.Flow

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