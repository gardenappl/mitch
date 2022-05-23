package ua.gardenapple.itchupdater.database.installation

import androidx.room.Embedded
import androidx.room.Ignore
import ua.gardenapple.itchupdater.database.game.Game

data class GameInstallation(
    @Embedded val game: Game,
    val status: Int,
    val downloadOrInstallId: Long?,
    val packageName: String? = null,
    val installId: Int,
    val uploadId: Int,
    val uploadName: String,
    val externalFileName: String? = null
) {
    @Ignore
    val librarySubtitle = if (packageName != null)
        game.author
    else if (uploadName == "-" || uploadName.isEmpty())
        game.author
    else
        uploadName

    companion object {
        val WEB_CACHE_DOWNLOAD_ID: Long? = null
        const val WEB_CACHE_INSTALL_ID = -1
        const val WEB_CACHE_UPLOAD_ID = -1
        const val WEB_CACHE_UPLOAD_NAME = ""

        fun createCachedWebGameInstallation(game: Game) = GameInstallation(
            game,
            Installation.STATUS_INSTALLED,
            installId = WEB_CACHE_INSTALL_ID,
            downloadOrInstallId = WEB_CACHE_DOWNLOAD_ID,
            uploadId = WEB_CACHE_UPLOAD_ID,
            uploadName = WEB_CACHE_UPLOAD_NAME
        )
    }
}