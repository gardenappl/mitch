package ua.gardenapple.itchupdater.database.game

import androidx.lifecycle.LiveData
import androidx.room.*
import ua.gardenapple.itchupdater.database.game.Game.Companion.GAME_ID
import ua.gardenapple.itchupdater.database.game.Game.Companion.TABLE_NAME
import ua.gardenapple.itchupdater.database.installation.GameInstallation
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.DOWNLOAD_OR_INSTALL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.EXTERNAL_FILE_NAME
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.INTERNAL_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.PACKAGE_NAME
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.STATUS
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.UPLOAD_ID
import ua.gardenapple.itchupdater.database.installation.Installation.Companion.UPLOAD_NAME

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
        WHERE $STATUS != ${Installation.STATUS_INSTALLED}""")
    abstract fun getPendingGames(): LiveData<List<GameInstallation>>


    @Query("SELECT * FROM $TABLE_NAME WHERE $GAME_ID = :gameId LIMIT 1")
    abstract suspend fun getGameById(gameId: Int): Game?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun insert(vararg games: Game)

    @Update
    abstract suspend fun update(vararg games: Game)

    @Transaction
    open suspend fun upsert(vararg games: Game) {
        for (game in games) {
            val existingGame = getGameById(game.gameId)
            if (existingGame == null)
                insert(game)
            else {
                update(game)
            }
        }
    }

    @Delete
    abstract suspend fun delete(games: List<Game>)
}