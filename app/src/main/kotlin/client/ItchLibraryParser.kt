package garden.appl.mitch.client

import garden.appl.mitch.Mitch
import garden.appl.mitch.client.ItchLibraryParser.PAGE_SIZE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

object ItchLibraryParser {
    private const val LOGGING_TAG = "ItchLibraryParser"

    private val thumbnailCssPattern = Regex("""background-image:\s+url\('([^']*)'\)""")

    const val PAGE_SIZE = 50

    /**
     * @return null if user is not logged in and has no access, otherwise a list of items (if size == [PAGE_SIZE], should request next page)
     */
    suspend fun parsePage(page: Int): List<ItchLibraryItem>? {
        val result = withContext(Dispatchers.IO) {
            val request = Request.Builder().run {
                url("https://itch.io/my-purchases?format=json&page=$page")
                get()
                build()
            }
            Mitch.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw IOException("Unexpected code $response")

                if (response.isRedirect)
                    return@withContext null

                response.body.string()
            }
        } ?: return null

        val resultJson = try {
            JSONObject(result)
        } catch (_: JSONException) {
            //Invalid JSON == we got redirected to login page
            return null
        }

        val itemCount = resultJson.getInt("num_items")
        val items = ArrayList<ItchLibraryItem>(itemCount)

        val document = Jsoup.parse(resultJson.getString("content"))

        var lastPurchaseDate: String? = null
        for (gameDiv in document.getElementsByClass("game_cell")) {
            var purchaseDate: String? = gameDiv.selectFirst(".date_header")
                ?.getElementsByTag("span")?.text()

            if (purchaseDate.isNullOrEmpty())
                purchaseDate = lastPurchaseDate
            else
                lastPurchaseDate = purchaseDate

            val thumbnailLink = gameDiv.selectFirst(".thumb_link")!!
            val downloadUrl = thumbnailLink.attr("href")
            val thumbnailImg = thumbnailLink.selectFirst("img")
            var thumbnailUrl = thumbnailImg?.attr("data-lazy_src")
            if (thumbnailUrl.isNullOrEmpty())
                thumbnailUrl = thumbnailImg?.attr("href")
            val title = gameDiv.selectFirst(".game_title")!!.text()
//            val description = gameDiv.selectFirst(".game_text").attr("title")
//            if (gameDiv.selectFirst(".game_text") == null) {
//                Log.d(LOGGING_TAG, "Game $title has no description, hmm")
//            }
            val author = gameDiv.selectFirst(".game_author")!!.text()
//            val isAndroid = gameDiv.selectFirst(".icon-android") != null
            val isAndroid = false

            items.add(
                ItchLibraryItem(
                    purchaseDate = purchaseDate!!,
                    downloadUrl = downloadUrl,
                    thumbnailUrl = thumbnailUrl,
                    title = title,
                    author = author,
//                    description = description,
                    isAndroid = isAndroid
                )
            )
        }

        return items
    }
}