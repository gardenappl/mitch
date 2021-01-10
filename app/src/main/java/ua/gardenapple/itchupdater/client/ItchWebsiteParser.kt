package ua.gardenapple.itchupdater.client

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
import org.jsoup.select.Elements
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.IOException
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.ArrayList

class ItchWebsiteParser {
    class UploadNotFoundException(uploadId: Int) : RuntimeException(uploadId.toString())
    
    data class DownloadUrl(val url: String, val isPermanent: Boolean, val isStorePage: Boolean) {
        val downloadKey: String?
            get() {
                if (isStorePage || !isPermanent)
                    return null
                return Uri.parse(url).lastPathSegment
            }

        override fun toString(): String {
            return "{ URL: $url, is permanent?: $isPermanent, is store page?: $isStorePage }"
        }
    }

    companion object {
        private const val LOGGING_TAG = "ItchWebsiteParser"
        const val UNKNOWN_LOCALE = "Unknown"
        const val ENGLISH_LOCALE = "en"

        fun getGameInfoForStorePage(storePageDoc: Document, gamePageUrl: String): Game {
            val thumbnails = storePageDoc.head().getElementsByAttributeValue("property", "og:image")
            var thumbnailUrl = ""
            if(thumbnails.isNotEmpty()) {
                thumbnailUrl = thumbnails[0].attr("content")
            } else {
                Log.d(LOGGING_TAG, "No thumbnail!")
            }


            val gameId: Int = ItchWebsiteUtils.getGameId(storePageDoc)
            val name: String = getGameName(storePageDoc)

            val infoTable = getInfoTable(storePageDoc)

            val authorName = getAuthorName(Uri.parse(gamePageUrl), infoTable)
            val lastDownloadTimestamp: String? = getTimestamp(infoTable)

            return Game(
                gameId = gameId,
                name = name,
                author = authorName,
                storeUrl = gamePageUrl,
                thumbnailUrl = thumbnailUrl,
                lastUpdatedTimestamp = lastDownloadTimestamp,
                locale = getLocale(storePageDoc)
            )
        }
        
        fun getInstallations(doc: Document): List<Installation> {
            return parseInstallations(doc, null)
        }

        fun getPendingInstallation(doc: Document, uploadId: Int, downloadId: Int): Installation {
            val installList = parseInstallations(doc, uploadId)
            if (installList.isEmpty())
                throw UploadNotFoundException(uploadId)
            else
                return installList.first().copy(
                    status = Installation.STATUS_DOWNLOADING,
                    downloadOrInstallId = downloadId
                )
        }
        
        private fun parseInstallations(doc: Document, requiredUploadId: Int?): List<Installation> {
            if (!ItchWebsiteUtils.hasGameDownloadLinks(doc))
                throw IllegalStateException("Unparse-able game page")

            val uploadDivs: List<Element>

            if (requiredUploadId != null) {
                val uploadButtons =
                    doc.getElementsByAttributeValue("data-upload_id", requiredUploadId.toString())
                if (uploadButtons.isEmpty()) {
                    if (doc.getElementsByClass("uploads").isNotEmpty())
                        throw UploadNotFoundException(requiredUploadId)
                    else
                        throw IllegalStateException("Unparse-able game page")
                }
                uploadDivs = Collections.singletonList(uploadButtons.first().parent())
            } else {
                uploadDivs = doc.getElementsByClass("upload_list_widget").first().children()
            }
            
            val locale = getLocale(doc)
            val gameId = ItchWebsiteUtils.getGameId(doc)

            val result = ArrayList<Installation>()
            
            for (uploadDiv in uploadDivs) {
                val icons = uploadDiv.getElementsByClass("icon")
                var platforms = Installation.PLATFORM_NONE

                for (icon in icons) {
                    if (icon.hasClass("icon-android"))
                        platforms = platforms or Installation.PLATFORM_ANDROID
                    else if (icon.hasClass("icon-windows8"))
                        platforms = platforms or Installation.PLATFORM_WINDOWS
                    else if (icon.hasClass("icon-apple"))
                        platforms = platforms or Installation.PLATFORM_MAC
                    else if (icon.hasClass("icon-tux"))
                        platforms = platforms or Installation.PLATFORM_LINUX
                }

                val uploadNameDiv = uploadDiv.getElementsByClass("upload_name")[0]
                val name = uploadNameDiv.getElementsByClass("name").attr("title")
                val fileSize = uploadNameDiv.getElementsByClass("file_size")[0].child(0).html()
                val uploadId = requiredUploadId ?:
                    Integer.parseInt(uploadDiv.getElementsByClass("download_btn")[0].attr("data-upload_id"))

                //These may or may not exist
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
                result.add(Installation(
                    gameId = gameId,
                    uploadId = uploadId,
                    locale = locale,
                    version = versionName,
                    uploadTimestamp = versionDate,
                    uploadName = name,
                    fileSize = fileSize,
                    platformFlags = platforms,
                ))
            }
            return result
        }

        fun getStoreUrlFromDownloadPage(downloadUri: Uri): String {
            return "https://${downloadUri.host}/${downloadUri.pathSegments[0]}"
        }

        fun getAuthorUrlFromGamePage(gamePageUri: Uri): String {
            return "https://${gamePageUri.host}"
        }



        suspend fun getDownloadUrl(doc: Document, storeUrl: String): DownloadUrl? = withContext(Dispatchers.IO) {
            //The game is free and the store page provides download links
            if(doc.getElementsByClass("download_btn").isNotEmpty())
                return@withContext DownloadUrl(storeUrl, isPermanent = true, isStorePage = true)

            //The game has been bought and the store page provides download links
            var elements = doc.getElementsByClass("purchase_banner_inner")
            if (elements.isNotEmpty()) {
                Log.d(LOGGING_TAG, "Purchased game")
                val downloadButtonRows = elements[0].getElementsByClass("key_row").sortedBy {
                    elements = it.getElementsByClass("purchase_price")

                    return@sortedBy if (elements.isNotEmpty()) {
                        Log.d(LOGGING_TAG, "Paid: ${elements[0].html().removePrefix("$").replace(".", "")}")
                        elements[0].html().removePrefix("$").replace(".", "").toInt()
                    } else 0
                }
                return@withContext DownloadUrl(downloadButtonRows.last()!!.child(0).attr("href"),
                    isPermanent = true, isStorePage = false)
            }

            //The game is free but accepts donations and hasn't been paid for
            return@withContext fetchDownloadUrlFromStorePage(storeUrl)
        }

        /**
         * Requests the URL of a web page with available downloads for an itch.io game.
         * This download URL might be temporary, or might not be available at all.
         * @param storeURL URL of a game's store page.
         * @return For paid games, always returns null, otherwise returns a URL of a downloads page.
         */
        suspend fun fetchDownloadUrlFromStorePage(storeUrl: String): DownloadUrl? = withContext(Dispatchers.IO) {
            Log.d(LOGGING_TAG, "Fetching download URL for $storeUrl")
            val storeUriParsed = Uri.parse(storeUrl)

            val uriBuilder = storeUriParsed.buildUpon()
            uriBuilder.appendPath("download_url")
            val getDownloadPathUri = uriBuilder.build()

            val form = FormBody.Builder().run {
                //TODO: Proper CSRF token support? For paid game update checking
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
            val resultJson = JSONObject(result)
            Log.d(LOGGING_TAG, "Result for $storeUrl: $resultJson")
            if (resultJson.has("errors")) {
                val errorsArray = resultJson.getJSONArray("errors")
                for (i in 0 until errorsArray.length()) {
                    if (errorsArray.getString(i) == "you must buy this game to download")
                        return@withContext null
                }

            }

            if (resultJson.has("url"))
                return@withContext DownloadUrl(resultJson.getString("url"),
                    isPermanent = false,
                    isStorePage = false
                )
            else
                throw RuntimeException("Unexpected JSON response")
        }

        fun getLocale(doc: Document): String {
            val scripts = doc.head().getElementsByTag("script")
            for (script in scripts) {
                val html = script.html().trimStart()
                if (html.startsWith("window.itchio_locale"))
                    return html.substring(24, 26)
            }

            if (doc.body().hasClass("locale_en"))
                return ENGLISH_LOCALE
            throw RuntimeException("Could not determine locale of web page")
        }

        private fun getTimestamp(infoTable: Element): String? {
            var timestamp = infoTable.child(0).child(1).child(0).attr("title")
            if(timestamp?.contains('@') != true)
                timestamp = null

            return timestamp
        }

        private fun getAuthorName(gamePageUri: Uri, infoTable: Element): String {
            return infoTable.getElementsByAttributeValue("href", getAuthorUrlFromGamePage(gamePageUri))[0].html()
        }

        /*fun getAuthorName(doc: Document, gamePageUri: Uri): String {
            return getAuthorName(gamePageUri, getInfoTable(doc))
        }*/

        private fun getInfoTable(doc: Document): Element {
            return doc.body().getElementsByClass("game_info_panel_widget")[0].child(0).child(0)
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

        fun getUserName(doc: Document): String {
            if (ItchWebsiteUtils.isUserPage(doc)) {
                return doc.getElementById("profile_header").getElementsByTag("h1")[0].text()
            }

            throw IllegalArgumentException("Document is not a user page")
        }
    }
}