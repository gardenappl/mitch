package ua.gardenapple.itchupdater.client

import android.util.Log
import kotlinx.coroutines.*
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.FLAVOR_ITCHIO
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.IOException
import java.lang.RuntimeException
import java.util.*
import kotlin.random.Random

class SingleUpdateChecker(val db: AppDatabase) {
    companion object {
        private const val LOGGING_TAG: String = "UpdateChecker"
        private const val RETRY_COUNT = 3
        private const val DELAY_MIN = 2000
        private const val DELAY_MAX = 5000
    }

    fun shouldCheck(installation: Installation): Boolean {
        return !(installation.gameId == Game.MITCH_GAME_ID && BuildConfig.FLAVOR != FLAVOR_ITCHIO)
    }

    suspend fun getDownloadInfo(currentGame: Game): Pair<Document, ItchWebsiteParser.DownloadUrl>? {
        for (i: Int in 0 until RETRY_COUNT) {
            try {
                return tryGetDownloadInfo(currentGame)
            } catch (e: IOException) {
                Log.e(LOGGING_TAG, "Error for ${currentGame.name}", e)

                if (i == RETRY_COUNT - 1) {
                    throw e
                } else {
                    logD(currentGame, "Retrying...")
                    delay(Random.nextInt(DELAY_MIN, DELAY_MAX).toLong())
                }
            }
        }
        throw RuntimeException()
    }

    /**
     * @return null if access is denied
     */
    private suspend fun tryGetDownloadInfo(currentGame: Game): Pair<Document, ItchWebsiteParser.DownloadUrl>? {
        var updateCheckDoc: Document
        var downloadPageInfo: ItchWebsiteParser.DownloadUrl
        var storePageDoc: Document? = null

        if (currentGame.downloadPageUrl != null) {
            //Have cached download URL

            updateCheckDoc = ItchWebsiteUtils.fetchAndParse(currentGame.downloadPageUrl)
            downloadPageInfo = currentGame.downloadInfo!!

            if (!ItchWebsiteUtils.hasGameDownloadLinks(updateCheckDoc)) {
                //game info may be out-dated
                storePageDoc = if (downloadPageInfo.isStorePage)
                    updateCheckDoc
                else
                    ItchWebsiteUtils.fetchAndParse(currentGame.storeUrl)

                downloadPageInfo =
                    ItchWebsiteParser.getDownloadUrl(storePageDoc, currentGame.storeUrl)
                        ?: return null

                updateCheckDoc = ItchWebsiteUtils.fetchAndParse(downloadPageInfo.url)
            }
        } else {
            //Must get fresh download URL

            storePageDoc = ItchWebsiteUtils.fetchAndParse(currentGame.storeUrl)
            downloadPageInfo =
                ItchWebsiteParser.getDownloadUrl(storePageDoc, currentGame.storeUrl)
                    ?: return null
            updateCheckDoc = ItchWebsiteUtils.fetchAndParse(downloadPageInfo.url)
        }


        //Update game metadata
        if (storePageDoc == null) {
            storePageDoc = if (downloadPageInfo.isStorePage)
                updateCheckDoc
            else
                ItchWebsiteUtils.fetchAndParse(currentGame.storeUrl)
        }

        var newGameInfo =
            ItchWebsiteParser.getGameInfoForStorePage(storePageDoc, currentGame.storeUrl)!!
        if (downloadPageInfo.isPermanent) {
            newGameInfo = newGameInfo.copy(downloadPageUrl = downloadPageInfo.url)
        }
        db.gameDao.update(newGameInfo)
        logD(currentGame, "Inserted new game info: $newGameInfo")

        return Pair(updateCheckDoc, downloadPageInfo)
    }


    suspend fun checkUpdates(
        currentGame: Game,
        currentInstall: Installation,
        updateCheckDoc: Document,
        downloadPageInfo: ItchWebsiteParser.DownloadUrl
    ): UpdateCheckResult {
        for (i: Int in 0 until RETRY_COUNT) {
            try {
                return tryCheckUpdates(currentGame, currentInstall,
                    updateCheckDoc, downloadPageInfo)
            } catch (e: IOException) {
                Log.e(LOGGING_TAG, "Error for ${currentGame.name}", e)
                if (i == RETRY_COUNT - 1) {
                    throw e
                } else {
                    logD(currentGame, "Retrying...")
                    delay(Random.nextInt(DELAY_MIN, DELAY_MAX).toLong())
                }
            }
        }
        throw RuntimeException()
    }

    private fun tryCheckUpdates(
        currentGame: Game, 
        currentInstall: Installation,
        updateCheckDoc: Document,
        downloadPageInfo: ItchWebsiteParser.DownloadUrl
    ): UpdateCheckResult {
        if (!shouldCheck(currentInstall))
            throw IllegalArgumentException("Should not be checking updates using itch.io for this")

        logD(currentGame, "Checking updates for ${currentGame.name}")
        logD(currentGame, "Current install: $currentInstall")

        if (!ItchWebsiteUtils.hasGameDownloadLinks(updateCheckDoc)) {
            logD(currentGame, "No download links!")
            return UpdateCheckResult(
                installationId = currentInstall.internalId,
                code = UpdateCheckResult.EMPTY
            )
        }

        return compareUploads(
            updateCheckDoc,
            currentInstall,
            currentGame,
            downloadPageInfo
        )
    }

    private fun compareUploads(
        updateCheckDoc: Document,
        currentInstall: Installation,
        game: Game,
        downloadPageUrl: ItchWebsiteParser.DownloadUrl
    ): UpdateCheckResult {
        val fetchedInstalls = ItchWebsiteParser.getInstallations(updateCheckDoc)

        var oneAvailableInstall = false
        var suggestedInstall: Installation? = null
        for (install in fetchedInstalls) {
            if (install.uploadName == currentInstall.uploadName) {
                suggestedInstall = install
                break
            }
            if (install.platforms and currentInstall.platforms == currentInstall.platforms) {
                if (suggestedInstall == null) {
                    suggestedInstall = install
                    oneAvailableInstall = true
                } else if (oneAvailableInstall) {
                    suggestedInstall = null
                    oneAvailableInstall = false
                }
            }
        }
        logD(game, "Suggested install: $suggestedInstall")

        for (install in fetchedInstalls) {
            // Note that just because the upload ID stays the same, it doesn't mean that there wasn't
            // an update. Builds pushed via butler don't change the upload ID.
            if (install.uploadId == currentInstall.uploadId) {
                logD(game, "Found same uploadId")
                    
                if (currentInstall.version != null) {
                    logD(game, "Checking version tags...")

                    if (currentInstall.version == install.version) {
                        logD(game, "Found same version tag")
                        
                        return UpdateCheckResult(currentInstall.internalId,
                            code = UpdateCheckResult.UP_TO_DATE)
                    } else if (isSameLocale(install, currentInstall)) {
                        logD(game, "Version tag changed!")

                        return UpdateCheckResult(
                            currentInstall.internalId,
                            downloadPageUrl = downloadPageUrl,
                            availableUpdateInstall = install
                        )
                    } else {
                        logD(game, "Version tag changed, but the locale is also different")
                        logD(game, "Or maybe current install version tag is null? That should not happen!")
                        if (install.uploadTimestamp?.equals(currentInstall.uploadTimestamp) == true) {
                            logD(game, "Timestamp is still the same, probably false positive")
                            return UpdateCheckResult(currentInstall.internalId,
                                code = UpdateCheckResult.UP_TO_DATE)
                        } else {
                            logD(game, "Timestamp changed! Might still be false positive but meh")

                            return UpdateCheckResult(
                                currentInstall.internalId,
                                downloadPageUrl = downloadPageUrl,
                                availableUpdateInstall = install
                            )
                        }
                    }
                } else {
                    logD(game, "Current install is not a butler upload")

                    if (install.version != null) {
                        logD(game, "Install became a butler upload? Weird but okay")

                        return UpdateCheckResult(
                            currentInstall.internalId,
                            downloadPageUrl = downloadPageUrl,
                            availableUpdateInstall = install
                        )
                    } else {
                        logD(game, "Nothing changed")

                        return UpdateCheckResult(currentInstall.internalId, UpdateCheckResult.UP_TO_DATE)
                    }
                }
            }
            if (currentInstall.uploadId == Installation.MITCH_UPLOAD_ID) {
                logD(game, "Checking version tags for Mitch...")

                if (install.version?.let { Utils.isVersionNewer(it, BuildConfig.VERSION_NAME) }
                    == true) {

                    logD(game, "Found newer Mitch version tag")
                    return UpdateCheckResult(currentInstall.internalId,
                        downloadPageUrl = downloadPageUrl,
                        availableUpdateInstall = suggestedInstall
                    )
                }
            }
        }
        logD(game, "Didn't find current uploadId")
        return UpdateCheckResult(currentInstall.internalId,
            downloadPageUrl = downloadPageUrl,
            availableUpdateInstall = suggestedInstall
        )
    }

    private fun isSameLocale(install1: Installation, install2: Installation): Boolean {
        return install1.locale == install2.locale && install1.locale != ItchWebsiteParser.UNKNOWN_LOCALE
    }

    private fun logD(game: Game, message: String) {
        Log.d(LOGGING_TAG, "(${game.name}) $message")
    }
}