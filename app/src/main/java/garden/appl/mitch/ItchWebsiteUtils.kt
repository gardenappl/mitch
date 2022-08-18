package garden.appl.mitch

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import garden.appl.mitch.ui.MitchWebView
import java.io.IOException
import java.net.URLEncoder

object ItchWebsiteUtils {
    val LOGIN_PAGE_URI: Uri = Uri.parse("https://itch.io/login")
    val STORE_ANDROID_PAGE_URI: Uri = Uri.parse("https://itch.io/games/platform-android")
    val STORE_PAGE_URI: Uri = Uri.parse("https://itch.io/games")

    private val forumJamGameBgColorPattern = Regex("--itchio_ui_bg: (#?\\w+);")
    private val gameButtonColorPattern = Regex("--itchio_button_color: (#?\\w+);")
    private val gameButtonFgColorPattern = Regex("--itchio_button_fg_color: (#?\\w+);")

    private val userBgColorPattern = Regex("--itchio_gray_back: (#?\\w+);")
    private val userFgColorPattern = Regex("--itchio_border_radius: ?\\w+;color:(#?\\w+);")

    private val jamLinkColorPattern = Regex("""\.view_jam_page\s+a[^{]*\{[^}]*color: (#?\w+);""")


    fun isItchWebPage(uri: Uri): Boolean {
        return uri.host != null && (uri.host == "itch.io" ||
                uri.host!!.endsWith(".itch.io") ||
                uri.host!!.endsWith(".itch.zone") ||
                uri.host!!.endsWith(".hwcdn.net"))
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

    fun isUserPage(htmlDoc: Document): Boolean {
        return htmlDoc.body().attr("data-page_name") == "user"
    }

    fun isDevlogPage(htmlDoc: Document): Boolean {
        return when (htmlDoc.body().attr("data-page_name")) {
            "game.devlog" -> true
            "game.devlog_post" -> true
            else -> false
        }
    }

    fun isJamOrForumPage(htmlDoc: Document): Boolean {
        return htmlDoc.head().getElementById("jam_theme") != null
    }

    fun isGamePage(htmlDoc: Document): Boolean {
        return isDevlogPage(htmlDoc) || isStorePage(htmlDoc) ||
                isPurchasePage(htmlDoc) || isDownloadPage(htmlDoc)
    }

    fun hasGameDownloadLinks(htmlDoc: Document): Boolean {
        return htmlDoc.body().selectFirst(".download_btn") != null
    }

    /**
     * Should you do day/night handling for this page?
     *
     * Note that most jam pages and forum pages have a few custom colors, but overall they do NOT
     * count as stylized pages, as they follow the standard rules for day/night themes
     * @return true if the page is a store page, devlog page, or stylized community profile
     */
    fun shouldHandleDayNightThemes(htmlDoc: Document): Boolean {
        return htmlDoc.selectFirst("#game_theme, #user_theme, [data-page_name=\"view_jam\"]") == null
    }

    /**
     * @return true if the screen is small enough where itch.io starts introducing the bottom navbar AND we're looking at a store page now
     */
    fun siteHasNavbar(webView: MitchWebView, htmlDoc: Document): Boolean {
        return webView.contentWidth < 650 && htmlDoc.getElementById("user_tools") != null
    }

    /**
     * @return If htmlDoc is a store page or download page, will return the associated gameID. Otherwise, the behavior is undefined.
     */
    fun getGameId(htmlDoc: Document): Int? {
        return htmlDoc.head().selectFirst("[name=\"itch:path\"]")
            ?.attr("content")
            ?.substringAfter("games/")?.toInt()
    }


    suspend fun fetchAndParse(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder().run {
            url(url)
            CookieManager.getInstance()?.getCookie(url)?.let { cookie ->
                addHeader("Cookie", cookie)
            }

            build()
        }
        Mitch.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw IOException("Unexpected response $response")

            val body = response.body
            body.byteStream().use { bodyStream ->
                return@withContext Jsoup.parse(bodyStream, body.contentType()?.charset()?.name(), url)
            }
        }
    }

    /**
     * @return the URL that the user will see by default on the Browse tab.
     */
    fun getMainBrowsePage(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return when (prefs.getString(PREF_BROWSE_START_PAGE, "web_touch")) {
            "android" -> "https://itch.io/games/platform-android"
            "web" -> "https://itch.io/games/platform-web"
            "web_touch" -> "https://itch.io/games/top-sellers/input-touchscreen/platform-web"
            "all_games" -> "https://itch.io/games"
            "library" -> "https://itch.io/my-collections"
            "feed" -> "https://itch.io/my-feed"
            else -> "https://itch.io"
        }
    }

    /**
     * @param searchQuery an itch.io search query, input from the user
     * @return the URL for search results on itch.io
     */
    fun getSearchUrl(searchQuery: String): String {
        val searchQueryEncoded = URLEncoder.encode(searchQuery, "utf-8")
        return "https://itch.io/search?q=$searchQueryEncoded"
    }

    /*
     * Methods for working with custom game themes or user themes
     */

    fun getBackgroundUIColor(doc: Document): Int? {
        val gameThemeCSS = doc.selectFirst("#game_theme, #jam_theme")?.html()
        if (gameThemeCSS != null) {
            val foundColors = forumJamGameBgColorPattern.find(gameThemeCSS)
            if (foundColors != null)
                return Utils.parseCssColor(foundColors.groupValues[1])
        }

        val userThemeCSS = doc.getElementById("user_theme")?.html()
        if (userThemeCSS != null)
            return Utils.parseCssColor("#333333")
        return null
    }

    fun getAccentUIColor(doc: Document): Int? {
        val gameThemeCSS = doc.selectFirst("#game_theme, #jam_theme")?.html()
        if (gameThemeCSS != null) {
            val foundColors = gameButtonColorPattern.find(gameThemeCSS)
            if (foundColors != null)
                return Utils.parseCssColor(foundColors.groupValues[1])
        }

        val userThemeCSS = doc.getElementById("user_theme")?.html()
        if (userThemeCSS != null) {
            val foundColors = userFgColorPattern.find(userThemeCSS)
            if (foundColors != null)
                return Utils.parseCssColor(foundColors.groupValues[1])
        }

        val jamThemeCSS = doc.selectFirst("#jam_theme")?.html()
        if (jamThemeCSS != null) {
            val foundColors = jamLinkColorPattern.find(jamThemeCSS)
            if (foundColors != null)
                return Utils.parseCssColor(foundColors.groupValues[1])
        }

        return null
    }


    fun getAccentFgUIColor(doc: Document): Int? {
        val gameThemeCSS = doc.selectFirst("#game_theme, #jam_theme")?.html()
        if (gameThemeCSS != null) {
            val foundColors = gameButtonFgColorPattern.find(gameThemeCSS)
            if (foundColors != null)
                return Utils.parseCssColor(foundColors.groupValues[1])
        }

        val userThemeCSS = doc.getElementById("user_theme")?.html()
        if (userThemeCSS != null) {
            val foundColors = userBgColorPattern.find(userThemeCSS)
            if (foundColors != null)
                return Utils.parseCssColor(foundColors.groupValues[1])
        }

        val jamThemeCSS = doc.selectFirst("#jam_theme")?.html()
        if (jamThemeCSS != null) {
            val foundColors = jamLinkColorPattern.find(jamThemeCSS)
            if (foundColors != null) {
                val color = Utils.parseCssColor(foundColors.groupValues[1])
                return if (Utils.shouldUseLightForeground(color))
                    R.color.colorPrimary
                else
                    R.color.colorPrimaryDark
            }
        }
        return null
    }

    fun isDarkTheme(doc: Document): Boolean {
        return doc.selectFirst(".main_layout")?.hasClass("dark_theme") ?: false
    }

    fun getLoggedInUserName(doc: Document): String? {
        return doc.selectFirst(".user_name")?.html()
    }
}