package ua.gardenapple.itchupdater.installer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation

class Installations {
    companion object {
        suspend fun deleteFinishedInstall(context: Context, uploadId: Int) =
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                db.installDao.deleteFinishedInstallation(uploadId)
                Mitch.fileManager.deleteDownloadedFile(uploadId)
            }

        suspend fun deleteOutdatedInstalls(context: Context, pendingInstall: Installation) {
            deleteFinishedInstall(context, pendingInstall.uploadId)

            if (pendingInstall.availableUploadIds == null)
                return

            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val finishedInstalls =
                    db.installDao.getFinishedInstallationsForGame(pendingInstall.gameId)

                for (finishedInstall in finishedInstalls) {
                    if (!pendingInstall.availableUploadIds.contains(finishedInstall.uploadId))
                        deleteFinishedInstall(context, finishedInstall.uploadId)
                }
            }
        }
    }
}