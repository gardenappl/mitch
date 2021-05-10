package ua.gardenapple.itchupdater.client

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.game.Game
import java.io.IOException
import java.lang.RuntimeException
import java.net.URL
import java.util.regex.Pattern

object JusticeBundleHandler {
    private const val LOGGING_TAG = "JusticeBundleHandler"

    private const val BUNDLE_URL = "https://itch.io/b/520/bundle-for-racial-justice-and-equality"
    private const val LINK_EMPTY = "NONE"
    private const val CACHE_KEEP_DAYS = 3

    /**
     * If a download link for this username has been cached, return it.
     * Otherwise, do network requests and parsing to get bundle download link.
     */
    suspend fun getLinkForUser(context: Context,
                                 userName: String?): String? = withContext(Dispatchers.IO) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        var link = sharedPrefs.getString(PREF_JUSTICE_LINK, LINK_EMPTY)
        if (link != LINK_EMPTY)
            return@withContext link


        if (userName == null)
            return@withContext null

        link = sharedPrefs.getString(PREF_PREFIX_JUSTICE_LINK + userName, LINK_EMPTY)
        if (link == LINK_EMPTY) {
            val timestamp = sharedPrefs.getLong(PREF_PREFIX_JUSTICE_LAST_CHECK + userName, 0)
            //TODO: Use [Instant] (requires higher API)
            if (System.currentTimeMillis() - timestamp < 1000L * 60 * 60 * 24 * CACHE_KEEP_DAYS)
                return@withContext null

            link = getBundleDownloadLink()

            sharedPrefs.edit().apply {
                putLong(PREF_PREFIX_JUSTICE_LAST_CHECK + userName, System.currentTimeMillis())
                putString(PREF_PREFIX_JUSTICE_LINK + userName, link ?: LINK_EMPTY)
                commit()
            }
        }
        return@withContext link
    }

    private suspend fun getBundleDownloadLink(): String? {
        val bundleDoc = ItchWebsiteUtils.fetchAndParse(BUNDLE_URL)

        val div = bundleDoc.getElementsByClass("existing_purchases")
        return div.first()?.getElementsByClass("button")?.first()?.attr("href")?.let { link ->
            if (link.startsWith("http"))
                link
            else
                URL(URL("https://itch.io"), link).toExternalForm()
        }
    }

    /**
     * Checks if this document is a download page for the Racial Justice bundle.
     * If it is, and the user is logged in, this [url] will be saved into cache
     * for the current username.
     */
    fun checkIsBundleLink(context: Context, doc: Document, url: String): Boolean {
        if (doc.body().attr("data-page_name") != "bundle_download")
            return false

        val name = ItchWebsiteUtils.getLoggedInUserName(doc)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPrefs.edit().apply {
            putString(PREF_PREFIX_JUSTICE_LINK + name, url)
            putLong(PREF_PREFIX_JUSTICE_LAST_CHECK, System.currentTimeMillis())
            apply()
        }
        return true
    }

    /**
     * @return download link for the newly or previously claimed game from the bundle.
     */
    suspend fun claimGame(bundleDownloadLink: String,
                          game: Game): String = withContext(Dispatchers.IO) {
        val searchUri = Uri.parse(bundleDownloadLink).buildUpon().run {
            appendQueryParameter("search", game.name)
            build()
        }
        val doc = ItchWebsiteUtils.fetchAndParse(searchUri.toString())

        //optional slash at the end
        val storePattern = Pattern.compile("""${Pattern.quote(game.storeUrl.trimEnd('/'))}\/?""")

        val rowDatas = doc.getElementsByClass("game_row_data")
        for (rowData in rowDatas) {
            if (rowData.getElementsByAttributeValueMatching("href", storePattern).isNotEmpty()) {
                val downloadButton = rowData.getElementsByClass("game_download_btn").first()
                if (downloadButton != null)
                    return@withContext downloadButton.attr("href")


                val formBuilder = FormBody.Builder()

                val formElement = rowData.getElementsByTag("form").first()!!
                for (input in formElement.getElementsByTag("input")) {
                    formBuilder.add(input.attr("name"), input.attr("value"))
                }
                formBuilder.add("action", "claim")

                val formRequest = Request.Builder().run {
                    url(searchUri.toString())

                    CookieManager.getInstance()?.getCookie("https://itch.io")?.let { cookie ->
                        addHeader("Cookie", cookie)
                    }
                    post(formBuilder.build())
                    build()
                }
                Mitch.httpClient.newCall(formRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(LOGGING_TAG, "Success: ${response.request.url}")
                        return@withContext response.request.url.toString()
                    }
                    if (response.isRedirect) {
                        Log.d(LOGGING_TAG, "Redirect: ${response.header("Location")}")
                        return@withContext response.header("Location")!!
                    }
                }
            }
        }
        throw RuntimeException("Could not find bundle download link for game $game")
    }
}