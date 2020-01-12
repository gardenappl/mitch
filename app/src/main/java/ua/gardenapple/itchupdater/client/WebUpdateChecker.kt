package ua.gardenapple.itchupdater.client

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload

class WebUpdateChecker {
    companion object {
        const val LOGGING_TAG: String = "WebUpdateChecker"
    }

    suspend fun checkUpdates(context: Context, gameId: Int): UpdateCheckResult = coroutineScope {
        val gameDao = AppDatabase.getDatabase(context).gameDao()

        val game: Game = withContext(Dispatchers.IO) {
            gameDao.getGameById(gameId)
        }

        val doc: Document = fetchDownloadPage(game)


        UpdateCheckResult.UNKNOWN
    }

    suspend fun fetchDownloadPage(game: Game): Document {
        val connectUrl = game.downloadPageUrl ?: game.storeUrl

        return Jsoup.connect(connectUrl).run {
            header("Cookie", CookieManager.getInstance().getCookie(connectUrl))
            withContext(Dispatchers.IO) {
                get()
            }
        }
    }

    fun getAndroidUploads(gameId: Int, doc: Document): List<Upload> {

        val uploadsList = ArrayList<Upload>()

        val icons = doc.getElementsByClass("icon-android")
        if (icons.isNotEmpty()) {
            var iconNum: Int = 0
            for(icon in icons) {
                val uploadNameDiv = icon.parent().parent()
                val name = uploadNameDiv.getElementsByClass("name").attr("title")
                val fileSize = uploadNameDiv.getElementsByClass("file_size")[0].child(0).html()


                //These may or may not exist
                val uploadId: Int? = uploadNameDiv.parent().previousElementSibling()?.attr("data-upload_id")!!.toInt()
                var versionName: String? = null
                var versionDate: String? = null

                val buildRow = uploadNameDiv.nextElementSibling()
                if(buildRow != null) {
                    if(buildRow.hasClass("upload_date"))
                        versionDate = buildRow.child(0).attr("title")

                    var elements = buildRow.getElementsByClass("version_name")
                    if(elements.isNotEmpty())
                        versionName = elements[0].html()

                    elements = buildRow.getElementsByClass("version_date")
                    if(elements.isNotEmpty())
                        versionDate = elements[0].child(0).attr("title")
                }


                uploadsList.add(Upload(
                    gameId = gameId,
                    uploadNum = iconNum,
                    uploadId = uploadId,
                    name = name,
                    fileSize = fileSize,
                    version = versionName,
                    uploadTimestamp = versionDate
                ))
                iconNum++
            }
        }
        return uploadsList
    }
}