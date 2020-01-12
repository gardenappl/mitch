package ua.gardenapple.itchupdater.database.game

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Game.TABLE_NAME)
data class Game(
    @PrimaryKey
    @ColumnInfo(name = GAME_ID)
    val gameID: Int,

    @ColumnInfo(name = NAME)
    val name: String,

    @ColumnInfo(name = AUTHOR)
    val author: String,

    @ColumnInfo(name = STORE_URL)
    val storeUrl: String,

    @ColumnInfo(name = DOWNLOAD_PAGE_URL)
    val downloadPageUrl: String?,

    @ColumnInfo(name = THUMBNAIL_URL)
    val thumbnailUrl: String
) {
    companion object {
        const val TABLE_NAME = "games"
        const val GAME_ID = "game_id"
        const val NAME = "name"
        const val AUTHOR = "author"
        const val STORE_URL = "store_url"
        const val DOWNLOAD_PAGE_URL = "download_page_url"
        const val THUMBNAIL_URL = "thumbnail_url"
    }
}