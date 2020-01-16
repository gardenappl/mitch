package ua.gardenapple.itchupdater.database.game

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached information about an itch.io project (could be a game, a tool, a comic book, etc.)
 * Should be written to the database on every page visit.
 */
@Entity(tableName = Game.TABLE_NAME,
    indices = [
        Index(value = [
            Game.STORE_URL
        ])
    ])
data class Game(
    @PrimaryKey
    @ColumnInfo(name = GAME_ID)
    val gameId: Int,

    @ColumnInfo(name = NAME)
    val name: String,

    @ColumnInfo(name = AUTHOR)
    val author: String,

    @ColumnInfo(name = STORE_URL)
    val storeUrl: String,

    /**
     * Set to null for games where the download page URL is not permament.
     */
    @ColumnInfo(name = DOWNLOAD_PAGE_URL)
    val downloadPageUrl: String? = null,

    @ColumnInfo(name = THUMBNAIL_URL)
    val thumbnailUrl: String,

    /**
     * Set to null for projects where the timestamp is not available.
     */
    @ColumnInfo(name = LAST_SEEN_TIMESTAMP)
    val lastDownloadTimestamp: String? = null,

    /**
     * Set to null for games if we haven't visited the store page yet, and don't have complete info.
     * Otherwise, set to UNIX time of last store page visit.
     */
    @ColumnInfo(name = STORE_LAST_VISITED)
    val storeLastVisited: Long? = null
) {
    companion object {
        const val MITCH_GAME_ID = 544475

        const val TABLE_NAME = "games"

        const val GAME_ID = "game_id"
        const val NAME = "name"
        const val AUTHOR = "author"
        const val STORE_URL = "store_url"
        const val DOWNLOAD_PAGE_URL = "download_page_url"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val LAST_SEEN_TIMESTAMP = "last_timestamp"
        const val STORE_LAST_VISITED = "store_last_visited"
    }
}