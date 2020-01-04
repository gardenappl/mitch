package ua.gardenapple.itchupdater

import android.net.Uri
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ui.MitchWebView

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
            val dataPageName = htmlDoc.body().attr("data-page_name")
            return dataPageName == "game_download" || dataPageName == "game_purchase"
        }

        /**
         * @return true if the screen is small enough where itch.io starts introducing the bottom navbar
         */
        fun shouldRemoveAppNavbar(webView: MitchWebView, htmlDoc: Document): Boolean {
            return webView.contentWidth < 650 && (isStorePage(htmlDoc) || isDownloadPage(htmlDoc))
        }
    }
}