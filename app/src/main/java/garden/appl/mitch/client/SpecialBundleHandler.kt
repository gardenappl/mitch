package garden.appl.mitch.client

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.Mitch
import garden.appl.mitch.data.SpecialBundle
import garden.appl.mitch.database.game.Game
import java.net.URL
import java.util.regex.Pattern

object SpecialBundleHandler {
    private const val LOGGING_TAG = "SpeicalBundleHandler"

    private const val LINK_EMPTY = "NONE"
    private const val CACHE_KEEP_DAYS = 3


    private fun getLinkPreferencesKeyPrefix(bundle: SpecialBundle): String {
        val prefLink = "mitch." + bundle.slug
        if (bundle == SpecialBundle.RacialJustice || bundle == SpecialBundle.Palestine)
            return prefLink + "_"
        else
            return prefLink + "."
    }

    private fun getTimestampPreferencesKey(bundle: SpecialBundle): String {
        val prefLink = "mitch." + bundle.slug
        if (bundle == SpecialBundle.RacialJustice || bundle == SpecialBundle.Palestine)
            return prefLink + "timestamp_"
        else
            return prefLink + "_timestamp."
    }

    /**
     * If a download link for this username has been cached, return it.
     * Otherwise, do network requests and parsing to get bundle download link.
     */
    suspend fun getLinkForUser(context: Context, bundle: SpecialBundle, userName: String?): String? {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        val prefLink = "mitch." + bundle.slug
        val prefLinkPrefix = getLinkPreferencesKeyPrefix(bundle)
        val prefTimestamp = getTimestampPreferencesKey(bundle)

        var link = sharedPrefs.getString(prefLink, LINK_EMPTY)
        if (link != LINK_EMPTY)
            return link

        if (userName == null)
            return null

        link = sharedPrefs.getString(prefLinkPrefix + userName, LINK_EMPTY)
        if (link == LINK_EMPTY) {
            val timestamp = sharedPrefs.getLong(prefTimestamp + userName, 0)
            //TODO: Use [Instant] (requires higher API)
            if (System.currentTimeMillis() - timestamp < 1000L * 60 * 60 * 24 * CACHE_KEEP_DAYS)
                return null

            link = getBundleDownloadLink(bundle.url)

            sharedPrefs.edit(commit = true) {
                putLong(prefTimestamp + userName, System.currentTimeMillis())
                putString(prefLinkPrefix + userName, link ?: LINK_EMPTY)
            }
        }
        return link
    }

    private suspend fun getBundleDownloadLink(bundleUrl: String): String? {
        val bundleDoc = ItchWebsiteUtils.fetchAndParse(bundleUrl)

        val div = bundleDoc.getElementsByClass("existing_purchases")
        return div.first()?.getElementsByClass("button")?.first()?.attr("href")?.let { link ->
            if (link.startsWith("http"))
                link
            else
                URL(URL("https://itch.io"), link).toExternalForm()
        }
    }

    /**
     * Checks if this document is a download page for the Racial Justice bundle,
     * or the Palestine Aid bundle.
     * If it is, and the user is logged in, this [url] will be saved into cache
     * for the current username.
     */
    fun checkIsBundleLink(context: Context, doc: Document, url: String): Boolean {
        if (doc.body().attr("data-page_name") != "bundle_download")
            return false

        var bundle: SpecialBundle? = null

        val titles = doc.getElementsByClass("object_title")
        for (title in titles) {
            val links = title.getElementsByTag("a")
            for (link in links) {
                for (possibleBundle in SpecialBundle.values()) {
                    if (link.attr("href").contains('/' + possibleBundle.bundleId.toString() + '/')) {
                        bundle = possibleBundle
                        break
                    }
                }
            }
        }
        if (bundle == null)
            return false

        val userName = ItchWebsiteUtils.getLoggedInUserName(doc)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPrefs.edit {
            putString(getLinkPreferencesKeyPrefix(bundle) + userName, url)
            putLong(getTimestampPreferencesKey(bundle) + userName, System.currentTimeMillis())
        }
        return true
    }

    /**
     * @return download link for the newly or previously claimed game from the bundle.
     */
    suspend fun claimGame(bundleDownloadLink: String, game: Game): String {
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
                    return downloadButton.attr("href")


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
                return withContext(Dispatchers.IO) {
                    Mitch.httpClient.newCall(formRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d(LOGGING_TAG, "Success: ${response.request.url}")
                            response.request.url.toString()
                        } else if (response.isRedirect) {
                            Log.d(LOGGING_TAG, "Redirect: ${response.header("Location")}")
                            response.header("Location")!!
                        } else {
                            throw RuntimeException("Could not find bundle download link for game $game")
                        }
                    }
                }
            }
        }
        throw RuntimeException("Could not find bundle download link for game $game")
    }
}