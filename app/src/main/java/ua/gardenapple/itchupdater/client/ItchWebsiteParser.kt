package ua.gardenapple.itchupdater.client

import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload
import java.io.IOException

class ItchWebsiteParser {
    data class DownloadUrl(val url: String, val isPermanent: Boolean)

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

        fun getStoreUrlFromDownloadPage(downloadUrl: String): String {
            val uri = Uri.parse(downloadUrl)
            return "${uri.scheme}://${uri.host}/${uri.pathSegments[0]}"
        }



        suspend fun getDownloadUrlFromStorePage(storeUrl: String): DownloadUrl = withContext(Dispatchers.IO) {
            val request = Request.Builder().run {
                url(storeUrl)
                addHeader("Cookie", CookieManager.getInstance().getCookie(storeUrl))
                build()
            }
            var html: String = ""
            MitchApp.httpClient.newCall(request).execute().use { response ->
                if(!response.isSuccessful)
                    throw IOException("Unexpected response $response")
                html = response.body!!.string()
            }

            val doc = withContext(Dispatchers.Default) {
                Jsoup.parse(html)
            }

            return@withContext getDownloadUrlFromStorePage(doc, storeUrl)
        }

        suspend fun getDownloadUrlFromStorePage(doc: Document, storeUrl: String): DownloadUrl = withContext(Dispatchers.IO) {
            //The game is free and the store page provides download links
            if(doc.getElementsByClass("download_btn").isNotEmpty())
                return@withContext DownloadUrl(storeUrl, true)

            //The game has been bought and the store page provides download links
            var elements = doc.getElementsByClass("purchase_banner_inner")
            if(elements.isNotEmpty()) {
                Log.d(LOGGING_TAG, "Purchased game")
                val downloadButtonRows = elements[0].getElementsByClass("key_row").sortedBy {
                    elements = it.getElementsByClass("purchase_price")

                    return@sortedBy if(elements.isNotEmpty()) {
                        Log.d(LOGGING_TAG, "Paid: ${elements[0].html().removePrefix("$").replace(".", "")}")
                        elements[0].html().removePrefix("$").replace(".", "").toInt()
                    } else 0
                }
                return@withContext DownloadUrl(downloadButtonRows.last()!!.child(0).attr("href"), true)
            }

            //The game is free and hasn't been bought but accepts donations (the tricky part)
            val storeUriParsed = Uri.parse(storeUrl)
            var uriBuilder = storeUriParsed.buildUpon()
            uriBuilder.appendPath("purchase")

            val purchaseUri = uriBuilder.build()

            var request = Request.Builder().run {
                url(purchaseUri.toString())
                build()
            }
//            MitchApp.httpClient.newCall(request).execute().use { response ->
//                if(!response.isSuccessful)
//                    throw IOException("Unexpected code $response")
//
//                val purchaseDoc = Jsoup.parse(response.body!!.string())
//            }

            uriBuilder = storeUriParsed.buildUpon()
            uriBuilder.appendPath("download_url")

            val getDownloadPathUri = uriBuilder.build()

            var form = FormBody.Builder().run {
                add("csrf_token", "0")
                build()
            }
            request = Request.Builder().run {
                url(getDownloadPathUri.toString())
                addHeader("Cookie", CookieManager.getInstance().getCookie(storeUrl))
                post(form)
                build()
            }
            val result: String = MitchApp.httpClient.newCall(request).execute().use { response ->
                if(!response.isSuccessful)
                    throw IOException("Unexpected code $response")

                response.body!!.string()
            }
            DownloadUrl(JSONObject(result).getString("url"), false)
        }
    }
}