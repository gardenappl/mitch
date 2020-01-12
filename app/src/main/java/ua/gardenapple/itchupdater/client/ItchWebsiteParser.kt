package ua.gardenapple.itchupdater.client

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload

class ItchWebsiteParser {
    companion object {
        const val LOGGING_TAG = "ItchWebsiteParser"

        fun getGameInfo(gamePageDoc: Document, gamePageUrl: String): Game {
            val thumbnailUrl: String = gamePageDoc.head().getElementsByAttributeValue("property", "og:image")[0].attr("href")

            val productJsonString: String = gamePageDoc.head().getElementsByAttributeValue("type", "application/ld+json")[1].html()
            val jsonObject = JSONObject(productJsonString)
            val name = jsonObject.getString("name")

            val gameId: Int = ItchWebsiteUtils.getGameId(gamePageDoc)

            val infoTable = gamePageDoc.body().getElementsByClass("game_info_panel_widget")[0].child(0).child(0)

            val authorUrl = "https://${Uri.parse(gamePageUrl).host}"
            val authorName = infoTable.getElementsByAttributeValue("href", authorUrl)[0].html()

            var lastDownloadTimestamp: String? = infoTable.child(0).child(1).child(0).attr("title")
            if(lastDownloadTimestamp?.contains('@') != true)
                lastDownloadTimestamp = null

            return Game(gameId, name, authorName, gamePageUrl, null, thumbnailUrl, lastDownloadTimestamp)
        }

        fun getAndroidUploads(gameId: Int, doc: Document): List<Upload> {

            val uploadsList = ArrayList<Upload>()

            val icons = doc.getElementsByClass("icon-android")
            if (icons.isNotEmpty()) {
                var iconNum: Int = 0

                for (icon in icons) {
                    val uploadNameDiv = icon.parent().parent()
                    val name = uploadNameDiv.getElementsByClass("name").attr("title")
                    val fileSize = uploadNameDiv.getElementsByClass("file_size")[0].child(0).html()

                    //These may or may not exist
                    val uploadId: Int? =
                        uploadNameDiv.parent().previousElementSibling()?.attr("data-upload_id")
                            ?.toInt()
                    var versionName: String? = null
                    var versionDate: String? = null

                    val buildRow = uploadNameDiv.nextElementSibling()
                    if (buildRow != null) {
                        if (buildRow.hasClass("upload_date"))
                            versionDate = buildRow.child(0).attr("title")

                        var elements = buildRow.getElementsByClass("version_name")
                        if (elements.isNotEmpty())
                            versionName = elements[0].html()

                        elements = buildRow.getElementsByClass("version_date")
                        if (elements.isNotEmpty())
                            versionDate = elements[0].child(0).attr("title")
                    }


                    uploadsList.add(
                        Upload(
                            gameId = gameId,
                            uploadNum = iconNum,
                            uploadId = uploadId,
                            name = name,
                            fileSize = fileSize,
                            version = versionName,
                            uploadTimestamp = versionDate
                        )
                    )
                    iconNum++
                }
            }
            return uploadsList
        }
    }
}