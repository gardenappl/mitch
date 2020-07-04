package ua.gardenapple.itchupdater.client

import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.upload.Upload
import java.io.IOException

class ItchWebsiteParser {
    data class DownloadUrl(val url: String, val isPermanent: Boolean, val isStorePage: Boolean) {
        fun getDownloadKey(): String? {
            if(isStorePage || !isPermanent)
                return null
            return Uri.parse(url).lastPathSegment
        }
    }

    companion object {
        const val LOGGING_TAG = "ItchWebsiteParser"
        const val UNKNOWN_LOCALE = "Unknown"

        private val gameBgColorPattern = Regex("root[{]--itchio_ui_bg: (#?\\w+);")
        private val gameButtonColorPattern = Regex("--itchio_button_color: (#?\\w+);")
        private val gameButtonFgColorPattern = Regex("--itchio_button_fg_color: (#?\\w+);")

        private val userBgColorPattern = Regex("--itchio_gray_back: (#?\\w+);")
        private val userFgColorPattern = Regex("--itchio_border_radius: ?\\w+;color:(#?\\w+);")
        private val userLinkColorPattern = Regex("--itchio_link_color: (#?\\w+);")

        fun getGameInfo(gamePageDoc: Document, gamePageUrl: String): Game {
            val thumbnails = gamePageDoc.head().getElementsByAttributeValue("property", "og:image")
            var thumbnailUrl = ""
            if(thumbnails.isNotEmpty()) {
                thumbnailUrl = thumbnails[0].attr("content")
            } else {
                Log.d(LOGGING_TAG, "No thumbnail!")
            }


            val gameId: Int = ItchWebsiteUtils.getGameId(gamePageDoc)
            val name: String = getGameName(gamePageDoc)

            val infoTable = gamePageDoc.body().getElementsByClass("game_info_panel_widget")[0].child(0).child(0)

            val authorUrl = "https://${Uri.parse(gamePageUrl).host}"
            val authorName = infoTable.getElementsByAttributeValue("href", authorUrl)[0].html()

            val lastDownloadTimestamp: String? = getTimestamp(gamePageDoc, infoTable)

            return Game(
                gameId = gameId,
                name = name,
                author = authorName,
                storeUrl = gamePageUrl,
                thumbnailUrl = thumbnailUrl,
                lastUpdatedTimestamp = lastDownloadTimestamp,
                locale = getLocale(gamePageDoc)
            )
        }

        fun getUploads(gameId: Int, doc: Document, setPending: Boolean = false): ArrayList<Upload> {
            val uploadsList = ArrayList<Upload>()

            val uploads = doc.getElementsByClass("upload")
            if (uploads.isNotEmpty()) {
                Log.d(LOGGING_TAG, "Found uploads: ${uploads.size}")
                var uploadNum: Int = 0

                for (uploadDiv in uploads) {
                    val icons = uploadDiv.getElementsByClass("icon")

                    var platforms = Upload.PLATFORM_NONE

                    for(icon in icons)
                    {
                        if(icon.hasClass("icon-android"))
                            platforms = platforms or Upload.PLATFORM_ANDROID
                        else if(icon.hasClass("icon-windows8"))
                            platforms = platforms or Upload.PLATFORM_WINDOWS
                        else if(icon.hasClass("icon-apple"))
                            platforms = platforms or Upload.PLATFORM_MAC
                        else if(icon.hasClass("icon-tux"))
                            platforms = platforms or Upload.PLATFORM_LINUX
                    }

                    val uploadNameDiv = uploadDiv.getElementsByClass("upload_name")[0]
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

                    val upload = Upload(
                        gameId = gameId,
                        uploadId = uploadId,
                        name = name,
                        fileSize = fileSize,
                        locale = getLocale(doc),
                        version = versionName,
                        uploadTimestamp = versionDate,
                        isPending = setPending,
                        platforms = platforms
                    )
                    Log.d(LOGGING_TAG, "Found upload: $upload")

                    uploadsList.add(upload)
                    uploadNum++
                }
            }
            return uploadsList
        }

        fun getStoreUrlFromDownloadPage(downloadUrl: String): String {
            val uri = Uri.parse(downloadUrl)
            return "${uri.scheme}://${uri.host}/${uri.pathSegments[0]}"
        }



//        suspend fun getDownloadUrlFromStorePage(storeUrl: String, doExtraFetches: Boolean): DownloadUrl? = withContext(Dispatchers.IO) {
//            val request = Request.Builder().run {
//                url(storeUrl)
//                addHeader("Cookie", CookieManager.getInstance().getCookie(storeUrl))
//                build()
//            }
//            var html: String = ""
//            MitchApp.httpClient.newCall(request).execute().use { response ->
//                if(!response.isSuccessful)
//                    throw IOException("Unexpected response $response")
//                html = response.body!!.string()
//            }
//
//            val doc = withContext(Dispatchers.Default) {
//                Jsoup.parse(html)
//            }
//
//            return@withContext getDownloadUrlFromStorePage(doc, storeUrl, doExtraFetches)
//        }


        suspend fun getDownloadUrlFromStorePage(doc: Document, storeUrl: String, doExtraFetches: Boolean): DownloadUrl? = withContext(Dispatchers.IO) {
            //The game is free and the store page provides download links
            if(doc.getElementsByClass("download_btn").isNotEmpty())
                return@withContext DownloadUrl(storeUrl, isPermanent = true, isStorePage = true)

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
                return@withContext DownloadUrl(downloadButtonRows.last()!!.child(0).attr("href"),
                    isPermanent = true, isStorePage = false)
            }

            //The game is free and hasn't been bought but accepts donations (the tricky part)
            return@withContext if(doExtraFetches)
                fetchDownloadUrlFromStorePage(storeUrl)
            else
                null
        }

        suspend fun fetchDownloadUrlFromStorePage(storeUrl: String): DownloadUrl? = withContext(Dispatchers.IO) {
            val storeUriParsed = Uri.parse(storeUrl)

            val uriBuilder = storeUriParsed.buildUpon()
            uriBuilder.appendPath("download_url")
            val getDownloadPathUri = uriBuilder.build()

            val form = FormBody.Builder().run {
                //Proper CSRF token support doesn't seem to be enforced for free games
                add("csrf_token", CookieManager.getInstance().getCookie(storeUrl))
                build()
            }
            val request = Request.Builder().run {
                url(getDownloadPathUri.toString())
                addHeader("Cookie", CookieManager.getInstance().getCookie(storeUrl))
                post(form)
                build()
            }
            val result: String =
                MitchApp.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful)
                        throw IOException("Unexpected code $response")

                    //Log.d(LOGGING_TAG, "Response: ${response.body!!.string()}")
                    response.body!!.string()
                }
            val jsonObject = JSONObject(result)
            return@withContext if (jsonObject.has("url"))
                DownloadUrl(jsonObject.getString("url"), false, false)
            else
                null
        }

        fun getLocale(doc: Document): String {
            val scripts = doc.head().getElementsByTag("script")
            for(script in scripts) {
                val html = script.html().trimStart()
                if(html.startsWith("window.itchio_locale"))
                    return html.substring(24, 26)
            }
            return UNKNOWN_LOCALE
        }

        fun getTimestamp(doc: Document, infoTable: Element? = null): String? {
            val theInfoTable = infoTable ?: doc.body().getElementsByClass("game_info_panel_widget")[0].child(0).child(0)

            var timestamp = theInfoTable.child(0).child(1).child(0).attr("title")
            if(timestamp?.contains('@') != true)
                timestamp = null

            return timestamp
        }

        fun getBackgroundUIColor(doc: Document): Int? {
            val gameThemeCSS = doc.getElementById("game_theme")?.html()
            if (gameThemeCSS != null) {
                val foundColors = gameBgColorPattern.find(gameThemeCSS)
                if (foundColors != null)
                    return Color.parseColor(foundColors.groupValues[1])
            }

            val userThemeCSS = doc.getElementById("user_theme")?.html()
            if (userThemeCSS != null) {
                val foundColors = userBgColorPattern.find(userThemeCSS)
                if (foundColors != null)
                    return Color.parseColor(foundColors.groupValues[1])
            }
            return Color.parseColor("#333")
        }

        fun getAccentUIColor(doc: Document): Int? {
            val gameThemeCSS = doc.getElementById("game_theme")?.html()
            if (gameThemeCSS != null) {
                val foundColors = gameButtonColorPattern.find(gameThemeCSS)
                if (foundColors != null)
                    return Color.parseColor(foundColors.groupValues[1])
            }

            val userThemeCSS = doc.getElementById("user_theme")?.html()
            if (userThemeCSS != null) {
                val foundColors = userFgColorPattern.find(userThemeCSS)
                if (foundColors != null)
                    return Color.parseColor(foundColors.groupValues[1])
            }
            return Color.parseColor("#fa5c5c")
        }

        fun getAccentFgUIColor(doc: Document): Int? {
            val gameThemeCSS = doc.getElementById("game_theme")?.html()
            if (gameThemeCSS != null) {
                val foundColors = gameButtonFgColorPattern.find(gameThemeCSS)
                if (foundColors != null)
                    return Color.parseColor(foundColors.groupValues[1])
            }

            val userThemeCSS = doc.getElementById("user_theme")?.html()
            if (userThemeCSS != null) {
                val foundColors = userBgColorPattern.find(userThemeCSS)
                if (foundColors != null)
                    return Color.parseColor(foundColors.groupValues[1])
            }
            return Color.parseColor("#fff")
        }

        fun getGameName(doc: Document): String {
            if (ItchWebsiteUtils.isPurchasePage(doc)) {
                return doc.getElementsByTag("h1")[0].child(0).text()
            }

            if (ItchWebsiteUtils.isDownloadPage(doc)) {
                return doc.getElementsByTag("h2")[0].child(0).text()
            }

            if (ItchWebsiteUtils.isStorePage(doc)) {
                val jsonObjects =
                    doc.head().getElementsByAttributeValue("type", "application/ld+json")
                val productJsonString: String = jsonObjects[1].html()
                val jsonObject = JSONObject(productJsonString)
                return jsonObject.getString("name")
            }

            if (ItchWebsiteUtils.isDevlogPage(doc)) {
                return doc.getElementsByClass("game_title")[0].html()
            }

            throw IllegalArgumentException("Document is not related to game")
        }
    }
}