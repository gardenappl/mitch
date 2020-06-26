package ua.gardenapple.itchupdater.client

import android.util.Log
import kotlinx.coroutines.*
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.upload.Upload

class WebUpdateChecker(val db: AppDatabase) {
    companion object {
        const val LOGGING_TAG: String = "WebUpdateChecker"
    }

    suspend fun checkUpdates(gameId: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {

            var game = db.gameDao.getGameById(gameId)
            if (game == null)
                throw IllegalStateException("Checking update for game ID $gameId with no Game info available")

            val currentInstall = db.installDao.findInstallation(gameId)
            if (currentInstall == null || currentInstall.status != Installation.STATUS_INSTALLED)
                throw IllegalStateException("Checking update for game ${game.name} (ID $gameId) which is not installed")

            Log.d(LOGGING_TAG, "Checking updates for ${game.name}")

            Log.d(LOGGING_TAG, "Current install: $currentInstall")

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
            db.gameDao.update(game)

            var updateCheckDoc = if (downloadPageInfo?.isStorePage == true)
                storePageDoc
            else
                ItchWebsiteUtils.fetchAndParseDocument(updateCheckUrl)
            var fetchedUploads = ItchWebsiteParser.getUploads(gameId, updateCheckDoc)



            if (fetchedUploads.isEmpty())
                return@withContext UpdateCheckResult(UpdateCheckResult.EMPTY)

            val result = compareUploads(db, updateCheckDoc, currentInstall, gameId, downloadPageInfo)

            if (result.uploadID != null)
                return@withContext result



            Log.d(LOGGING_TAG, "Bringing out the big guns")

            downloadPageInfo = ItchWebsiteParser.fetchDownloadUrlFromStorePage(game.storeUrl)
            if (downloadPageInfo == null)
                return@withContext UpdateCheckResult(UpdateCheckResult.ACCESS_DENIED)

            updateCheckUrl = downloadPageInfo.url
            updateCheckDoc = ItchWebsiteUtils.fetchAndParseDocument(updateCheckUrl)

            return@withContext compareUploads(db, updateCheckDoc, currentInstall, gameId, downloadPageInfo)
        }

    private fun compareUploads(
        db: AppDatabase,
        updateCheckDoc: Document,
        currentInstall: Installation,
        gameId: Int,
        downloadPageUrl: ItchWebsiteParser.DownloadUrl?
    ): UpdateCheckResult {
        val fetchedUploads = ItchWebsiteParser.getUploads(gameId, updateCheckDoc)

        Log.d(LOGGING_TAG, "Looking for local upload info ${currentInstall.uploadId}")
        val installedUpload = db.uploadDao.getUploadById(currentInstall.uploadId)!!
        Log.d(LOGGING_TAG, "Found $installedUpload")
        var suggestedUpload: Upload? = null

        for (upload in fetchedUploads) {
            if (upload.name == installedUpload.name) {
                suggestedUpload = upload
                break
            }
            if (upload.platforms == installedUpload.platforms)
                suggestedUpload = upload
        }
        Log.d(LOGGING_TAG, "Suggested upload: $suggestedUpload")

        //Try different heuristics

        /*
        Note that just because the upload ID stays the same, it doesn't mean that there wasn't
        an update. Builds pushed via butler don't change the upload ID.
         */
        var allVersionsDifferent = true
        if(fetchedUploads[0].uploadId != null && currentInstall.gameId != Game.MITCH_GAME_ID) {
            Log.d(LOGGING_TAG, "Checking upload IDs...")
            for (upload in fetchedUploads) {
                if (upload.uploadId == currentInstall.uploadId) {
                    Log.d(LOGGING_TAG, "Found same upload ID")
                    allVersionsDifferent = false

                    if(upload.locale == installedUpload.locale && upload.version != installedUpload.version) {
                        Log.d(LOGGING_TAG, "Version tag changed")
                        return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, upload.uploadId, downloadPageUrl, updateCheckDoc)
                    }
                    if(upload.fileSize != installedUpload.fileSize) {
                        Log.d(LOGGING_TAG, "File size changed")
                        return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, upload.uploadId, downloadPageUrl, updateCheckDoc)
                    }
                    if(upload.locale == installedUpload.locale && upload.uploadTimestamp != installedUpload.uploadTimestamp) {
                        Log.d(LOGGING_TAG, "Timestamp changed")
                        return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, upload.uploadId, downloadPageUrl, updateCheckDoc)
                    }

                    Log.d(LOGGING_TAG, "Suggested upload: $suggestedUpload")
                    suggestedUpload = upload
                    break
                }
            }
            if (allVersionsDifferent) {
                Log.d(LOGGING_TAG, "All upload IDs are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }

        allVersionsDifferent = true
        if(installedUpload.gameId == Game.MITCH_GAME_ID && installedUpload.locale == Game.MITCH_LOCALE) {
            Log.d(LOGGING_TAG, "Checking version tags...")
            Log.d(LOGGING_TAG, "Special processing for Mitch")
            for(upload in fetchedUploads) {
                if(upload.version!!.contains(installedUpload.version!!)) {
                    Log.d(LOGGING_TAG, "Found same version tag")
                    allVersionsDifferent = false
                    break
                }
            }
            if(allVersionsDifferent) {
                Log.d(LOGGING_TAG, "All version tags are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        } else if (installedUpload.version != null && (fetchedUploads[0].locale == installedUpload.locale)) {
            Log.d(LOGGING_TAG, "Checking version tags...")
            for (upload in fetchedUploads) {
                if (upload.version == installedUpload.version) {
                    Log.d(LOGGING_TAG, "Found same version tag")
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent) {
                Log.d(LOGGING_TAG, "All version tags are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }

        if (fetchedUploads[0].uploadTimestamp != null && fetchedUploads[0].locale == installedUpload.locale) {
            Log.d(LOGGING_TAG, "Checking timestamps...")
            allVersionsDifferent = true
            for (upload in fetchedUploads) {
                if (upload.uploadTimestamp == installedUpload.uploadTimestamp) {
                    Log.d(LOGGING_TAG, "Found same timestamp")
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent) {
                Log.d(LOGGING_TAG, "All timestamps are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }

        if(installedUpload.fileSize != Upload.MITCH_FILE_SIZE) {
            Log.d(LOGGING_TAG, "Checking file sizes...")
            allVersionsDifferent = true
            for (upload in fetchedUploads) {
                if (upload.fileSize == installedUpload.fileSize) {
                    Log.d(LOGGING_TAG, "Found same file size")
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent) {
                Log.d(LOGGING_TAG, "All file sizes different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }


        val currentUploads = db.uploadDao.getUploadsForGame(gameId)
        if (currentUploads.size == fetchedUploads.size) {
            Log.d(LOGGING_TAG, "Same amount of uploads, performing extra checks...")

            var allVersionsSame = true
            if(installedUpload.gameId == Game.MITCH_GAME_ID && installedUpload.locale == Game.MITCH_LOCALE) {
                Log.d(LOGGING_TAG, "Checking version tags...")
                Log.d(LOGGING_TAG, "Special processing for Mitch")
                for(upload in fetchedUploads) {
                    if(!upload.version!!.contains(installedUpload.version!!)) {
                        Log.d(LOGGING_TAG, "Version tags don't match")
                        allVersionsSame = false
                        break
                    }
                }
                if (allVersionsSame) {
                    Log.d(LOGGING_TAG, "Version tags all match")
                    return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE)
                }
            } else if (installedUpload.version != null) {
                Log.d(LOGGING_TAG, "Checking version tags...")
                for (i in fetchedUploads.indices) {
                    if (fetchedUploads[i].version != currentUploads[i].version) {
                        Log.d(LOGGING_TAG, "Version tags don't match")
                        allVersionsSame = false
                        break
                    }
                }
                if (allVersionsSame) {
                    Log.d(LOGGING_TAG, "Version tags all match")
                    return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE)
                }
            }

            if (installedUpload.uploadTimestamp != null) {
                Log.d(LOGGING_TAG, "Checking timestamps...")
                allVersionsSame = true
                for (i in fetchedUploads.indices) {
                    if (fetchedUploads[i].uploadTimestamp != currentUploads[i].uploadTimestamp) {
                        Log.d(LOGGING_TAG, "Timestamps don't match")
                        allVersionsSame = false
                        break
                    }
                }
                if (allVersionsSame) {
                    Log.d(LOGGING_TAG, "Timestamps all match")
                    return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE)
                }
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