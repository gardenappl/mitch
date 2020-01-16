package ua.gardenapple.itchupdater.client

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.upload.Upload

class WebUpdateChecker(val context: Context) {
    companion object {
        const val LOGGING_TAG: String = "WebUpdateChecker"
    }

    suspend fun checkUpdates(gameId: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)

            var game = db.gameDao.getGameById(gameId)
            Log.d(LOGGING_TAG, "Checking updates for ${game.name}")

            val currentInstall = db.installationDao.findInstallation(gameId)
            if (currentInstall == null || !currentInstall.downloadFinished)
                throw IllegalStateException("Checking update for game ${game.name} (ID $gameId) which is not installed")

//        game = updateStoreInfoIfNecessary(game)

            val storePageDoc = ItchWebsiteUtils.fetchAndParseDocument(game.storeUrl)
            game = ItchWebsiteParser.getGameInfo(storePageDoc, game.storeUrl)

            var updateCheckUrl = game.downloadPageUrl
            var downloadPageInfo: ItchWebsiteParser.DownloadUrl? = null

            if (updateCheckUrl == null) {
                downloadPageInfo = ItchWebsiteParser.getDownloadUrlFromStorePage(
                    storePageDoc,
                    game.storeUrl,
                    false
                )

                updateCheckUrl = downloadPageInfo?.url ?: game.storeUrl
            }

            if (downloadPageInfo?.isPermanent == true)
                game = game.copy(downloadPageUrl = downloadPageInfo.url)

            //Received new info about the game, save to database.
            db.gameDao.insert(game)

            var updateCheckDoc = if (downloadPageInfo?.isStorePage == true)
                storePageDoc
            else
                ItchWebsiteUtils.fetchAndParseDocument(updateCheckUrl)
            var fetchedUploads = ItchWebsiteParser.getAndroidUploads(gameId, updateCheckDoc)



            if (fetchedUploads.isEmpty())
                return@withContext UpdateCheckResult(UpdateCheckResult.EMPTY)

            val result = compareUploads(db, fetchedUploads, currentInstall, gameId)

            if (result.code != UpdateCheckResult.UNKNOWN)
                return@withContext result



            Log.d(LOGGING_TAG, "Bringing out the big guns")

            downloadPageInfo = ItchWebsiteParser.fetchDownloadUrlFromStorePage(game.storeUrl)
            if (downloadPageInfo == null)
                return@withContext UpdateCheckResult(UpdateCheckResult.ACCESS_DENIED)

            updateCheckUrl = downloadPageInfo.url
            updateCheckDoc = ItchWebsiteUtils.fetchAndParseDocument(updateCheckUrl)
            fetchedUploads = ItchWebsiteParser.getAndroidUploads(gameId, updateCheckDoc)

            return@withContext compareUploads(db, fetchedUploads, currentInstall, gameId)
        }

    private fun compareUploads(
        db: AppDatabase,
        fetchedUploads: ArrayList<Upload>,
        currentInstall: Installation,
        gameId: Int
    ): UpdateCheckResult {

        val installedUpload = db.uploadDao.getUploadByInternalId(currentInstall.uploadIdInternal)
        var suggestedUpload: Upload? = null

        if (fetchedUploads.size == 1)
            suggestedUpload = fetchedUploads.first()
        else {
            for (upload in fetchedUploads) {
                if (upload.name == installedUpload.name)
                    suggestedUpload = upload
            }
        }
        Log.d(LOGGING_TAG, "Suggested upload: $suggestedUpload")

        //Try different heuristics

        /*
        Note that just because the upload ID stays the same, it doesn't mean that there wasn't
        an update. Builds pushed via butler don't change the upload ID.
         */
        var allVersionsDifferent = true
        if (fetchedUploads[0].uploadId != null) {
            for (upload in fetchedUploads) {
                if (upload.uploadId == currentInstall.uploadIdInternal) {
                    suggestedUpload = upload
                    Log.d(LOGGING_TAG, "Suggested upload: $suggestedUpload")
                    allVersionsDifferent = false
                    break
                }
            }
            if(allVersionsDifferent)
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId)
        }

        allVersionsDifferent = true
        if(installedUpload.gameId == Game.MITCH_GAME_ID && installedUpload.locale == Game.MITCH_LOCALE) {
            Log.d(LOGGING_TAG, "Special processing for Mitch...")
            for(upload in fetchedUploads) {
                if(upload.version?.contains(installedUpload.version!!) == true) {
                    allVersionsDifferent = false
                    break
                }
            }
        }
        if(allVersionsDifferent)
            return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId)

        allVersionsDifferent = true
        if (installedUpload.version != null && (fetchedUploads[0].locale == installedUpload.locale)) {
            for (upload in fetchedUploads) {
                if (upload.version == installedUpload.version) {
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent)
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId)
        }

        allVersionsDifferent = true
        if (installedUpload.uploadTimestamp != null && fetchedUploads[0].locale == installedUpload.locale) {
            for (upload in fetchedUploads) {
                if (upload.uploadTimestamp == installedUpload.uploadTimestamp) {
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent)
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId)
        }

        allVersionsDifferent = true
        for (upload in fetchedUploads) {
            if (upload.fileSize == installedUpload.fileSize) {
                allVersionsDifferent = false
                break
            }
        }
        if (allVersionsDifferent)
            return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId)


        val currentUploads = db.uploadDao.getUploadsForGame(gameId)
        if (currentUploads.size == fetchedUploads.size) {

            var allVersionsSame = true
            if (installedUpload.version != null && fetchedUploads[0].locale == installedUpload.locale) {
                for (i in 0..fetchedUploads.size) {
                    if (fetchedUploads[i].version != currentUploads[i].version) {
                        allVersionsSame = false
                        break
                    }
                }
                if (allVersionsSame)
                    return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE)
            }

            allVersionsSame = true
            if (installedUpload.uploadTimestamp != null && fetchedUploads[0].locale == installedUpload.locale) {
                for (i in 0..fetchedUploads.size) {
                    if (fetchedUploads[i].uploadTimestamp != currentUploads[i].uploadTimestamp) {
                        allVersionsSame = false
                        break
                    }
                }
                if (allVersionsSame)
                    return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE)
            }
        }

        return UpdateCheckResult(UpdateCheckResult.UNKNOWN)
    }

//    private suspend fun updateStoreInfoIfNecessary(game: Game): Game = withContext(Dispatchers.IO) {
//        val currentTime = Utils.getCurrentUnixTime()
//        if(game.storeLastVisited != null) {
//            val secondsDiff = currentTime - game.storeLastVisited
//            if(secondsDiff >= 0 && secondsDiff < 60 * 5) //5 minutes
//                return@withContext game
//        }
//
//        val doc = ItchWebsiteUtils.fetchAndParseDocument(game.storeUrl)
//
//        return@withContext ItchWebsiteParser.getGameInfo(doc, game.storeUrl)
//    }
}