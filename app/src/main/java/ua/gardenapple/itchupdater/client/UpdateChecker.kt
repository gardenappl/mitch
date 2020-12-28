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
import java.lang.IllegalArgumentException
import java.util.*

class UpdateChecker(val db: AppDatabase) {
    companion object {
        private const val LOGGING_TAG: String = "UpdateChecker"
    }

    fun shouldCheck(installation: Installation): Boolean {
        return !(installation.gameId == Game.MITCH_GAME_ID && BuildConfig.FLAVOR != FLAVOR_ITCHIO)
    }

    /**
     * @return null if access is denied
     */
    suspend fun getDownloadInfo(currentGame: Game): Pair<Document, ItchWebsiteParser.DownloadUrl>? =
        withContext(Dispatchers.IO) {
            var updateCheckDoc: Document
            var downloadPageInfo: ItchWebsiteParser.DownloadUrl

            if (currentGame.downloadPageUrl != null) {
                //Have cached download URL

                updateCheckDoc = ItchWebsiteUtils.fetchAndParse(currentGame.downloadPageUrl)
                downloadPageInfo = currentGame.downloadInfo!!

                if (!ItchWebsiteUtils.hasGameDownloadLinks(updateCheckDoc)) {
                    //game info may be out-dated
                    val storePageDoc = if (downloadPageInfo.isStorePage)
                        updateCheckDoc
                    else
                        ItchWebsiteUtils.fetchAndParse(currentGame.storeUrl)

                    downloadPageInfo =
                        ItchWebsiteParser.getDownloadUrl(storePageDoc, currentGame.storeUrl)
                            ?: return@withContext null

                    if (downloadPageInfo.isPermanent) {
                        //insert new info
                        val newGameInfo =
                            ItchWebsiteParser.getGameInfoForStorePage(
                                storePageDoc,
                                currentGame.storeUrl
                            )
                        db.gameDao.update(newGameInfo.copy(downloadPageUrl = downloadPageInfo.url))
                    }
                    updateCheckDoc = ItchWebsiteUtils.fetchAndParse(downloadPageInfo.url)
                }
            } else {
                //Must get fresh download URL

                val storePageDoc = ItchWebsiteUtils.fetchAndParse(currentGame.storeUrl)
                downloadPageInfo =
                    ItchWebsiteParser.getDownloadUrl(storePageDoc, currentGame.storeUrl)
                        ?: return@withContext null
                updateCheckDoc = ItchWebsiteUtils.fetchAndParse(downloadPageInfo.url)
            }
            return@withContext Pair(updateCheckDoc, downloadPageInfo)
        }

    suspend fun checkUpdates(
        currentGame: Game, 
        currentInstall: Installation,
        updateCheckDoc: Document,
        downloadPageInfo: ItchWebsiteParser.DownloadUrl
    ): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (!shouldCheck(currentInstall))
                throw IllegalArgumentException("Should not be checking updates using itch.io for this")

            logD(currentGame, "Checking updates for ${currentGame.name}")
            logD(currentGame, "Current install: $currentInstall")

            if (!ItchWebsiteUtils.hasGameDownloadLinks(updateCheckDoc)) {
                return@withContext UpdateCheckResult(
                    installationId = currentInstall.internalId,
                    code = UpdateCheckResult.ACCESS_DENIED
                )
            }

            logD(currentGame, "Update check URL: ${downloadPageInfo.url}")

            return@withContext compareUploads(updateCheckDoc, currentInstall, currentGame, downloadPageInfo)
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
                    val containsCurrentVersion = install.version?.contains(currentInstall.version)
                    if (containsCurrentVersion == true) {
                        logD(game, "Found same version tag")
                        
                        return UpdateCheckResult(currentInstall.internalId,
                            code = UpdateCheckResult.UP_TO_DATE)
                    } else if (containsCurrentVersion == false) {
                        logD(game, "Version tag changed!")

                        return UpdateCheckResult(
                            currentInstall.internalId,
                            code = UpdateCheckResult.UPDATE_NEEDED,
                            downloadPageUrl = downloadPageUrl,
                            uploadID = install.uploadId,
                            newVersionString = install.version,
                            newTimestamp = install.uploadTimestamp,
                            newSize = install.fileSize
                        )
                    } else {
                        throw IllegalStateException("Version tag unknown? This should not happen")
                    }
                } else {
                    logD(game, "Current install is not a butler upload")
                    if (install.version != null) {
                        logD(game, "Install became a butler upload? Weird but okay")

                        return UpdateCheckResult(
                            currentInstall.internalId,
                            code = UpdateCheckResult.UPDATE_NEEDED,
                            downloadPageUrl = downloadPageUrl,
                            uploadID = install.uploadId,
                            newVersionString = install.version,
                            newTimestamp = install.uploadTimestamp,
                            newSize = install.fileSize
                        )
                    } else {
                        logD(game, "Nothing changed")

                        return UpdateCheckResult(currentInstall.internalId, UpdateCheckResult.UP_TO_DATE)
                    }
                }
            }
        }
        logD(game, "Didn't find current uploadId")
        return UpdateCheckResult(currentInstall.internalId,
            UpdateCheckResult.UPDATE_NEEDED,
            uploadID = suggestedInstall?.uploadId,
            downloadPageUrl = downloadPageUrl,
            newTimestamp = suggestedInstall?.uploadTimestamp,
            newVersionString = suggestedInstall?.version,
            newSize = suggestedInstall?.fileSize
        )
    }

    private fun isSameLocale(install1: Installation, install2: Installation): Boolean {
        return install1.locale == install2.locale && install1.locale != ItchWebsiteParser.UNKNOWN_LOCALE
    }

    private fun logD(game: Game, message: String) {
        Log.d(LOGGING_TAG, "(${game.name}) $message")
    }
}