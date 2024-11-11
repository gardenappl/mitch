package garden.appl.mitch.database.game

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import garden.appl.mitch.database.game.Game.Companion.GAME_ID
import garden.appl.mitch.database.game.Game.Companion.TABLE_NAME
import garden.appl.mitch.database.installation.GameInstallation
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.database.installation.Installation.Companion.DOWNLOAD_OR_INSTALL_ID
import garden.appl.mitch.database.installation.Installation.Companion.EXTERNAL_FILE_NAME
import garden.appl.mitch.database.installation.Installation.Companion.INTERNAL_ID
import garden.appl.mitch.database.installation.Installation.Companion.PACKAGE_NAME
import garden.appl.mitch.database.installation.Installation.Companion.STATUS
import garden.appl.mitch.database.installation.Installation.Companion.UPLOAD_ID
import garden.appl.mitch.database.installation.Installation.Companion.UPLOAD_NAME

@Dao
abstract class GameDao {
    @Query("SELECT * FROM $TABLE_NAME")
    abstract fun getAllKnownGames(): LiveData<List<Game>>

    @Query("SELECT * FROM $TABLE_NAME")
    abstract suspend fun getAllKnownGamesSync(): List<Game>


    @Query("""
        SELECT games.*, installations.$STATUS as status, 
            installations.$DOWNLOAD_OR_INSTALL_ID as downloadOrInstallId,
            installations.$PACKAGE_NAME as packageName,
            installations.$INTERNAL_ID as installId,
            installations.$UPLOAD_NAME as uploadName,
            installations.$UPLOAD_ID as uploadId,
            installations.$EXTERNAL_FILE_NAME as externalFileName
        FROM games INNER JOIN installations
        ON games.$GAME_ID = installations.game_id
        WHERE $STATUS = ${Installation.STATUS_INSTALLED} AND
            $PACKAGE_NAME IS NOT NULL""")
    abstract fun getInstalledAndroidGames(): LiveData<List<GameInstallation>>


    @Query("""
        SELECT games.*, installations.$STATUS as status, 
            installations.$DOWNLOAD_OR_INSTALL_ID as downloadOrInstallId,
            installations.$PACKAGE_NAME as packageName,
            installations.$INTERNAL_ID as installId,
            installations.$UPLOAD_NAME as uploadName,
            installations.$UPLOAD_ID as uploadId,
            installations.$EXTERNAL_FILE_NAME as externalFileName
        FROM games INNER JOIN installations
        ON games.$GAME_ID = installations.game_id
        WHERE $STATUS = ${Installation.STATUS_INSTALLED} AND
            $PACKAGE_NAME IS NULL""")
    abstract fun getGameDownloads(): LiveData<List<GameInstallation>>


    @Query("""
        SELECT games.*, installations.$STATUS as status, 
            installations.$DOWNLOAD_OR_INSTALL_ID as downloadOrInstallId,
            installations.$PACKAGE_NAME as packageName,
            installations.$INTERNAL_ID as installId,
            installations.$UPLOAD_NAME as uploadName,
            installations.$UPLOAD_ID as uploadId,
            installations.$EXTERNAL_FILE_NAME as externalFileName
        FROM games INNER JOIN installations
        ON games.$GAME_ID = installations.game_id
        WHERE $STATUS != ${Installation.STATUS_INSTALLED} 
            AND $STATUS != ${Installation.STATUS_WEB_CACHED}""")
    abstract fun getPendingGames(): LiveData<List<GameInstallation>>


    @Query("""
        SELECT games.*, installations.$STATUS as status, 
            installations.$DOWNLOAD_OR_INSTALL_ID as downloadOrInstallId,
            installations.$PACKAGE_NAME as packageName,
            installations.$INTERNAL_ID as installId,
            installations.$UPLOAD_NAME as uploadName,
            installations.$UPLOAD_ID as uploadId,
            installations.$EXTERNAL_FILE_NAME as externalFileName
        FROM games INNER JOIN installations
        ON games.$GAME_ID = installations.game_id
        WHERE $STATUS = ${Installation.STATUS_WEB_CACHED}""")
    abstract fun getCachedWebGames(): LiveData<List<GameInstallation>>


    @Query("""
        SELECT games.name
        FROM games INNER JOIN installations
        ON games.$GAME_ID = installations.game_id
        WHERE $STATUS != ${Installation.STATUS_INSTALLING} 
            AND $DOWNLOAD_OR_INSTALL_ID = :downloadId
        LIMIT 1""")
    abstract suspend fun getNameForPendingInstallWithDownloadId(downloadId: Int): String?


    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId LIMIT 1")
    abstract suspend fun getGameById(gameId: Int): Game?

    /**
     * @return -1 if a Game with the same [Game.GAME_ID] already exists
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun insert(game: Game): Long

    @Update
    abstract suspend fun update(game: Game)

    @Transaction
    open suspend fun upsert(game: Game) {
        val id = insert(game)
        if (id == -1L) {
            update(game)
        }
    }

    @Delete
    abstract suspend fun delete(games: List<Game>)
}