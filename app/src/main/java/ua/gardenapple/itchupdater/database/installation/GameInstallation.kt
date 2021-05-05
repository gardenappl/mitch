package ua.gardenapple.itchupdater.database.installation

import androidx.room.Embedded
import androidx.room.Ignore
import ua.gardenapple.itchupdater.database.game.Game

data class GameInstallation(
    @Embedded val game: Game,
    val status: Int,
    val downloadOrInstallId: Long?,
    val packageName: String?,
    val installId: Int,
    val uploadId: Int,
    val uploadName: String,
    val externalFileName: String?
) {
    @Ignore
    val librarySubtitle = if (packageName != null)
        game.author
    else if (uploadName == "-")
        ""
    else
        uploadName
}