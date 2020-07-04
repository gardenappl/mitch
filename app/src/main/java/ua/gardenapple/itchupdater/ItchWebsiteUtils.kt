package ua.gardenapple.itchupdater

import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ui.MitchWebView
import java.io.IOException
import java.net.URLEncoder

class ItchWebsiteUtils {
    companion object {
        fun isItchWebPage(uri: Uri): Boolean {
            return uri.host == "itch.io" ||
                    uri.host!!.endsWith(".itch.io") ||
                    uri.host!!.endsWith(".itch.zone") ||
                    uri.host!!.endsWith(".hwcdn.net")
        }

        fun isStorePage(htmlDoc: Document): Boolean {
            return htmlDoc.body().attr("data-page_name") == "view_game"
        }

        fun isDownloadPage(htmlDoc: Document): Boolean {
            return htmlDoc.body().attr("data-page_name") == "game_download"
        }

        fun isPurchasePage(htmlDoc: Document): Boolean {
            return htmlDoc.body().attr("data-page_name") == "game_purchase"
        }

        fun isDevlogPage(htmlDoc: Document): Boolean {
            return when(htmlDoc.body().attr("data-page_name")) {
                "game.devlog" -> true
                "game.devlog_post" -> true
                else -> false
            }
        }

        fun hasGameDownloadLinks(htmlDoc: Document): Boolean {
            return htmlDoc.body().getElementsByClass("download_btn").isNotEmpty()
        }

        /**
         * @return true if the page is a store page or devlog page
         */
        fun isStylizedGamePage(htmlDoc: Document): Boolean {
            return htmlDoc.getElementById("game_theme") != null
                    && htmlDoc.getElementById("user_tools") != null
        }

        /**
         * @return true if the page is a store page or devlog page, or stylized community profile
         */
        fun isStylizedPage(htmlDoc: Document): Boolean {
            return htmlDoc.getElementById("game_theme") != null
        }

        /**
         * @return true if the screen is small enough where itch.io starts introducing the bottom navbar AND we're looking at a store page now
         */
        fun siteHasNavbar(webView: MitchWebView, htmlDoc: Document): Boolean {
            return webView.contentWidth < 650 && isStylizedGamePage(htmlDoc)
        }

        /**
         * @return If htmlDoc is a store page or download page, will return the associated gameID. Otherwise, the behavior is undefined.
         */
        fun getGameId(htmlDoc: Document): Int {
            return htmlDoc.head().getElementsByAttributeValue("name", "itch:path")[0].attr("content")
                .substringAfter("games/").toInt()
        }


        suspend fun fetchAndParseDocument(url: String): Document = withContext(Dispatchers.IO) {
            val request = Request.Builder().run {
                url(url)
                addHeader("Cookie", CookieManager.getInstance().getCookie(url))
                build()
            }
            var html = ""
            MitchApp.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw IOException("Unexpected response $response")
                html = response.body!!.string()
            }

            return@withContext Jsoup.parse(html)
        }

        /**
         * @return the URL that the user will see by default on the Browse tab.
         * TODO: add preferences to change this
         */
        fun getMainBrowsePage(): String {
            return "https://itch.io/games/platform-android"
        }

        /**
         * @param searchQuery an itch.io search query, input from the user
         * @return the URL for search results on itch.io
         */
        fun getSearchUrl(searchQuery: String): String {
            val searchQueryEncoded = URLEncoder.encode(searchQuery, "utf-8")
            return "https://itch.io/search?q=$searchQueryEncoded"
        }
    }
}