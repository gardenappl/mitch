package ua.gardenapple.itchupdater.database.game

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ua.gardenapple.itchupdater.client.ItchWebsiteParser

/**
 * Cached information about an itch.io project (could be a game, a tool, a comic book, etc.)
 * Should be written to the database on every page visit.
 * All this data should be accessible by parsing the game's store page.
 */
@Entity(tableName = Game.TABLE_NAME)
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

    /**
     * Some games don't have thumbnails
     */
    @ColumnInfo(name = THUMBNAIL_URL)
    val thumbnailUrl: String?,

    /**
     * Affects timestamps and version strings.
     */
    @ColumnInfo(name = LOCALE)
    val locale: String = ItchWebsiteParser.UNKNOWN_LOCALE,

    /**
     * Set to null for projects where the timestamp is not available.
     */
    @ColumnInfo(name = LAST_UPDATED_TIMESTAMP)
    val lastUpdatedTimestamp: String? = null
) {
    companion object {
        const val MITCH_GAME_ID = 544475
        const val MITCH_LOCALE = "[Mitch locale]"
        const val MITCH_STORE_PAGE = "https://gardenapple.itch.io/mitch"

        const val TABLE_NAME = "games"

        const val GAME_ID = "game_id"
        const val NAME = "name"
        const val AUTHOR = "author"
        const val STORE_URL = "store_url"
        const val DOWNLOAD_PAGE_URL = "download_page_url"
        const val LOCALE = "locale"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val LAST_UPDATED_TIMESTAMP = "last_timestamp"
    }
}