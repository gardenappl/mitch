package ua.gardenapple.itchupdater.database.installation

import androidx.room.*
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload


@Entity(tableName = Installation.TABLE_NAME,
    indices = [
        Index(value = [Installation.UPLOAD_ID])
    ])
data class Installation(
    /**
     * Corresponds to an Upload's uploadId (which should always non-null for files which have actually been downloaded).
     * Mitch has a hardcoded upload ID. (see [MITCH_UPLOAD_ID])
     */
    @PrimaryKey
    @ColumnInfo(name = UPLOAD_ID)
    val uploadId: Int,

    /**
     * Set to null for games where the timestamp is not available.
     */
    @ColumnInfo(name = TIMESTAMP)
    val storeVisitTimestamp: String? = null,

    /**
     * Set to null for downloads which are not installable.
     */
    @ColumnInfo(name = PACKAGE_NAME)
    val packageName: String? = null
) {
    companion object {
        const val TABLE_NAME = "installations"

        const val UPLOAD_ID = "upload_id"
        const val PACKAGE_NAME = "name"
        const val TIMESTAMP = "timestamp"

        val MITCH_UPLOAD_ID = Upload.calculateInternalId(null, Game.MITCH_GAME_ID, 0)
    }
}