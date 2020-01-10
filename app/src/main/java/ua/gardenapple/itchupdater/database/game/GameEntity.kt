package ua.gardenapple.itchupdater.database.game

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val gameID: Int,
    val name: String,
    val author: String,
    val storeURL: String,
    val downloadURL: String?,
    val thumbnailURL: String
)