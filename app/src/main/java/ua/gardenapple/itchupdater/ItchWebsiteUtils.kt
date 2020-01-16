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

        /**
         * @return true if the screen is small enough where itch.io starts introducing the bottom navbar
         */
        fun shouldRemoveAppNavbar(webView: MitchWebView, htmlDoc: Document): Boolean {
            return webView.contentWidth < 650 && (isStorePage(htmlDoc) || isDownloadPage(htmlDoc) || isPurchasePage(htmlDoc))
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
                if(!response.isSuccessful)
                    throw IOException("Unexpected response $response")
                html = response.body!!.string()
            }

            return@withContext Jsoup.parse(html)
        }
    }
}