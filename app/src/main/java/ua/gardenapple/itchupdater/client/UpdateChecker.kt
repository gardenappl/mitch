package ua.gardenapple.itchupdater.client

import android.util.Log
import kotlinx.coroutines.*
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.FLAVOR_ITCHIO
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.upload.Upload

class UpdateChecker(val db: AppDatabase) {
    companion object {
        const val LOGGING_TAG: String = "UpdateChecker"

        fun shouldCheck(gameId: Int): Boolean {
            return !(gameId == Game.MITCH_GAME_ID && BuildConfig.FLAVOR != FLAVOR_ITCHIO)
        }
    }

    suspend fun checkUpdates(gameId: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (!shouldCheck(gameId))
                throw IllegalStateException("Should not be checking updates using itch.io for this")
            var game = db.gameDao.getGameById(gameId)
            if (game == null)
                throw IllegalStateException("Checking update for game ID $gameId with no Game info available")

            val currentInstall = db.installDao.findInstallation(gameId)
            if (currentInstall == null || currentInstall.status != Installation.STATUS_INSTALLED)
                throw IllegalStateException("Checking update for game ${game.name} (ID $gameId) which is not installed")

            logD(game, "Checking updates for ${game.name}")

            logD(game, "Current install: $currentInstall")

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

            logD(game, "Update check URL: $updateCheckUrl")

            if (downloadPageInfo?.isPermanent == true)
                game = game.copy(downloadPageUrl = downloadPageInfo.url)

            //Received new info about the game, save to database.
            db.gameDao.update(game)

            var updateCheckDoc = if (downloadPageInfo?.isStorePage == true)
                storePageDoc
            else
                ItchWebsiteUtils.fetchAndParseDocument(updateCheckUrl)

            val result = compareUploads(db, updateCheckDoc, currentInstall, gameId, downloadPageInfo)
            logD(game, "Update check result: $result")

            if (result.uploadID != null ||
                (result.code != UpdateCheckResult.ACCESS_DENIED && result.code != UpdateCheckResult.UNKNOWN))
                return@withContext result



            logD(game, "Bringing out the big guns")

            downloadPageInfo = ItchWebsiteParser.fetchDownloadUrlFromStorePage(game.storeUrl)
            if (downloadPageInfo == null)
                return@withContext UpdateCheckResult(UpdateCheckResult.UNKNOWN)


            updateCheckUrl = downloadPageInfo.url
            updateCheckDoc = ItchWebsiteUtils.fetchAndParseDocument(updateCheckUrl)

            logD(game, "Download page is $updateCheckUrl")

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

        if (fetchedUploads.isEmpty())
            return UpdateCheckResult(UpdateCheckResult.EMPTY)
            
        val game = db.gameDao.getGameById(gameId)!!

        logD(game, "Looking for local upload info ${currentInstall.uploadId}")
        val installedUpload = db.uploadDao.getUploadById(currentInstall.uploadId)!!
        logD(game, "Found $installedUpload")
        var suggestedUpload: Upload? = null

        var oneAvailableUpload: Boolean = false
        for (upload in fetchedUploads) {
            if (upload.name == installedUpload.name) {
                suggestedUpload = upload
                break
            }
            if (upload.platforms == installedUpload.platforms) {
                if (suggestedUpload == null) {
                    suggestedUpload = upload
                    oneAvailableUpload = true
                } else if (oneAvailableUpload) {
                    suggestedUpload = null
                    oneAvailableUpload = false
                }
            }
        }
        logD(game, "Suggested upload: $suggestedUpload")

        //Try different heuristics

        /*
        Note that just because the upload ID stays the same, it doesn't mean that there wasn't
        an update. Builds pushed via butler don't change the upload ID.
         */
        var allVersionsDifferent = true
        if(fetchedUploads[0].uploadId != null && currentInstall.gameId != Game.MITCH_GAME_ID) {
            logD(game, "Checking upload IDs...")
            for (upload in fetchedUploads) {
                if (upload.uploadId == currentInstall.uploadId) {
                    logD(game, "Found same upload ID")
                    allVersionsDifferent = false

                    if(isSameLocale(installedUpload, upload) && upload.version != installedUpload.version) {
                        logD(game, "Version tag changed")
                        return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, upload.uploadId, downloadPageUrl, updateCheckDoc)
                    }
                    else if (installedUpload.version == null && upload.version == null) {
                        logD(game, "Version tag unchanged, not a butler upload")
                        return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE, updateCheckDoc = updateCheckDoc)
                    }

                    if(upload.fileSize != installedUpload.fileSize) {
                        logD(game, "File size changed")
                        return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, upload.uploadId, downloadPageUrl, updateCheckDoc)
                    }
                    if(isSameLocale(installedUpload, upload) && upload.uploadTimestamp != installedUpload.uploadTimestamp &&
                            installedUpload.uploadTimestamp != null) {
                        logD(game, "Timestamp changed")
                        return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, upload.uploadId, downloadPageUrl, updateCheckDoc)
                    }

                    logD(game, "Upload with current uploadID has not changed")
                    suggestedUpload = null
                    break
                }
            }
            if (allVersionsDifferent) {
                logD(game, "All upload IDs are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }

        allVersionsDifferent = true
        if(installedUpload.gameId == Game.MITCH_GAME_ID && installedUpload.locale == Game.MITCH_LOCALE) {
            logD(game, "Checking version tags...")
            logD(game, "Special processing for Mitch")
            for(upload in fetchedUploads) {
                if(upload.version!!.contains(installedUpload.version!!)) {
                    logD(game, "Found same version tag")
                    allVersionsDifferent = false
                    break
                }
            }
            if(allVersionsDifferent) {
                logD(game, "All version tags are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        } else if (installedUpload.version != null && isSameLocale(installedUpload, fetchedUploads[0])) {
            logD(game, "Checking version tags...")
            for (upload in fetchedUploads) {
                if (upload.version == installedUpload.version) {
                    logD(game, "Found same version tag")
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent) {
                logD(game, "All version tags are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }

        if (installedUpload.uploadTimestamp != null && isSameLocale(installedUpload, fetchedUploads[0])) {
            logD(game, "Checking timestamps...")
            allVersionsDifferent = true
            for (upload in fetchedUploads) {
                if (upload.uploadTimestamp == installedUpload.uploadTimestamp) {
                    logD(game, "Found same timestamp")
                    allVersionsDifferent = false
                    break
                }
                if (upload.uploadTimestamp == null) {
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent) {
                logD(game, "All timestamps are different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }

        if(installedUpload.fileSize != Upload.MITCH_FILE_SIZE) {
            logD(game, "Checking file sizes...")
            allVersionsDifferent = true
            for (upload in fetchedUploads) {
                if (upload.fileSize == installedUpload.fileSize) {
                    logD(game, "Found same file size")
                    allVersionsDifferent = false
                    break
                }
            }
            if (allVersionsDifferent) {
                logD(game, "All file sizes different")
                return UpdateCheckResult(UpdateCheckResult.UPDATE_NEEDED, suggestedUpload?.uploadId, downloadPageUrl, updateCheckDoc)
            }
        }


        val currentUploads = db.uploadDao.getUploadsForGame(gameId)
        if (currentUploads.size == fetchedUploads.size) {
            logD(game, "Same amount of uploads, performing extra checks...")

            var allVersionsSame = true
            if(installedUpload.gameId == Game.MITCH_GAME_ID && installedUpload.locale == Game.MITCH_LOCALE) {
                logD(game, "Checking version tags...")
                logD(game, "Special processing for Mitch")
                for(upload in fetchedUploads) {
                    if(!upload.version!!.contains(installedUpload.version!!)) {
                        logD(game, "Version tags don't match")
                        allVersionsSame = false
                        break
                    }
                }
                if (allVersionsSame) {
                    logD(game, "Version tags all match")
                    return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE, updateCheckDoc = updateCheckDoc)
                }
            } else if (installedUpload.version != null) {
                if (isSameLocale(installedUpload, fetchedUploads[0])) {
                    logD(game, "Checking version tags...")
                    for (i in fetchedUploads.indices) {
                        if (fetchedUploads[i].version != currentUploads[i].version) {
                            logD(game, "Version tags don't match")
                            allVersionsSame = false
                            break
                        }
                    }
                    if (allVersionsSame) {
                        logD(game, "Version tags all match")
                        return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE, updateCheckDoc = updateCheckDoc)
                    }
                }
            }

            if (installedUpload.uploadTimestamp != null) {
                if (isSameLocale(installedUpload, fetchedUploads[0])) {
                    logD(game, "Checking timestamps...")
                    allVersionsSame = true
                    for (i in fetchedUploads.indices) {
                        if (fetchedUploads[i].uploadTimestamp != currentUploads[i].uploadTimestamp) {
                            logD(game, "Timestamps don't match")
                            allVersionsSame = false
                            break
                        }
                    }
                    if (allVersionsSame) {
                        logD(game, "Timestamps all match")
                        return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE, updateCheckDoc = updateCheckDoc)
                    }
                }
            } else {
                logD(game, "Current timestamp is null")
                return UpdateCheckResult(UpdateCheckResult.UP_TO_DATE, updateCheckDoc = updateCheckDoc)
            }
        }

        return UpdateCheckResult(UpdateCheckResult.UNKNOWN, updateCheckDoc = updateCheckDoc)
    }

    private fun isSameLocale(upload1: Upload, upload2: Upload): Boolean {
        return upload1.locale == upload2.locale && upload1.locale != ItchWebsiteParser.UNKNOWN_LOCALE
    }

    private fun logD(game: Game, message: String) {
        Log.d(LOGGING_TAG, "(${game.name}) $message")
    }
}