package ua.gardenapple.itchupdater.data

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import ua.gardenapple.itchupdater.client.ItchAccessDeniedException
import ua.gardenapple.itchupdater.client.ItchLibraryItem
import ua.gardenapple.itchupdater.client.ItchLibraryParser
import java.io.IOException

class ItchLibraryPagingSource : PagingSource<Int, ItchLibraryItem>() {

    companion object {
        private const val ITCH_LIBRARY_STARTING_PAGE_INDEX = 1
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ItchLibraryItem> {
        val pageNum = params.key ?: ITCH_LIBRARY_STARTING_PAGE_INDEX
        
        try {
            val items = ItchLibraryParser.parsePage(pageNum)

            if (items == null)
                return LoadResult.Error(ItchAccessDeniedException("No access to owned library, is user logged in?"))

            return LoadResult.Page(
                data = items,
                prevKey = if (pageNum == ITCH_LIBRARY_STARTING_PAGE_INDEX) null else pageNum - 1,
                nextKey = if (items.size == ItchLibraryParser.PAGE_SIZE) pageNum + 1 else null
            )
        } catch (e: IOException) {
            return LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ItchLibraryItem>): Int? {
        // We need to get the previous key (or next key if previous is null) of the page
        // that was closest to the most recently accessed index.
        // Anchor position is the most recently accessed index
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}