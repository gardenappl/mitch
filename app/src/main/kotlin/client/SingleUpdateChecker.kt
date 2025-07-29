package garden.appl.mitch.client

import android.util.Log
import garden.appl.mitch.BuildConfig
import garden.appl.mitch.FLAVOR_ITCHIO
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import org.jsoup.nodes.Document
import java.io.IOException

class SingleUpdateChecker(val db: AppDatabase) {
    companion object {
        private const val LOGGING_TAG: String = "UpdateChecker"
    }

    data class DownloadInfo(
        val updateCheckDoc: Document? = null,
        val downloadUrl: ItchWebsiteParser.DownloadUrl? = null,
        val exception: Exception? = null,
        val accessDenied: Boolean = false
    )

    fun shouldCheck(installation: Installation): Boolean {
        return !(installation.gameId == Game.MITCH_GAME_ID && BuildConfig.FLAVOR != FLAVOR_ITCHIO)
    }

    suspend fun getDownloadInfo(currentGame: Game): DownloadInfo {
        try {
            return tryGetDownloadInfo(currentGame)
        } catch (e: IOException) {
            Log.e(LOGGING_TAG, "Error for ${currentGame.name}", e)

            return DownloadInfo(exception = e)
        }
    }

    private suspend fun tryGetDownloadInfo(currentGame: Game): DownloadInfo {
        var updateCheckDoc: Document
        var downloadPageInfo: ItchWebsiteParser.DownloadUrl
        var storePageDoc: Document? = null

        if (currentGame.downloadPageUrl != null) {
            // Have cached download URL
            updateCheckDoc = ItchWebsiteUtils.fetchAndParse(currentGame.downloadPageUrl)
            downloadPageInfo = currentGame.downloadInfo!!

            if (downloadPageInfo.isStorePage) {
                storePageDoc = updateCheckDoc
            }
            if (!ItchWebsiteUtils.hasGameDownloadLinks(updateCheckDoc)) {
                // Game info may be out-dated
                if (storePageDoc == null) {
                    storePageDoc = ItchWebsiteUtils.fetchAndParse(currentGame.storeUrl)
                }

                downloadPageInfo =
                    ItchWebsiteParser.getOrFetchDownloadUrl(currentGame.storeUrl, storePageDoc)
                        ?: return DownloadInfo(accessDenied = true)

                updateCheckDoc = if (downloadPageInfo.isStorePage) {
                    storePageDoc
                } else {
                    ItchWebsiteUtils.fetchAndParse(downloadPageInfo.url)
                }
            }
        } else {
            // Must get fresh download URL
            downloadPageInfo = ItchWebsiteParser.fetchDownloadUrl(currentGame.storeUrl)
                ?: return DownloadInfo(accessDenied = true)
            updateCheckDoc = ItchWebsiteUtils.fetchAndParse(downloadPageInfo.url)
        }

        // Update game metadata, if possible
        storePageDoc?.let { doc ->
            val game = ItchWebsiteParser.getGameInfoForStorePage(doc, currentGame.storeUrl)!!
            db.gameDao.update(game)
            logD(currentGame, "Inserted new game info: $game")
        }

        return DownloadInfo(
            updateCheckDoc = updateCheckDoc,
            downloadUrl = downloadPageInfo
        )
    }

    fun checkUpdates(
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
                } else {
                    logD(game, "Mitch version tag is not newer: ${install.version}, current is ${BuildConfig.VERSION_NAME}")

                    return UpdateCheckResult(currentInstall.internalId, UpdateCheckResult.UP_TO_DATE)
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