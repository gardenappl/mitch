package garden.appl.mitch.database.installation

import androidx.room.Embedded
import androidx.room.Ignore
import garden.appl.mitch.database.game.Game

data class GameInstallation(
    @Embedded val game: Game,
    val status: Int,
    val downloadOrInstallId: Long?,
    val packageName: String? = null,
    val installId: Int,
    val uploadId: Int,
    val uploadName: String,
    val externalFileUri: String? = null,
    val externalFileName: String? = null
) {
    @Ignore
    val librarySubtitle = if (packageName != null || status == Installation.STATUS_WEB_CACHED)
        game.author
    else if (uploadName == "-" || uploadName.isEmpty())
        game.author
    else
        uploadName
}