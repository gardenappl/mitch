package ua.gardenapple.itchupdater.client

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import ua.gardenapple.itchupdater.Mitch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ItchLibraryParser {
    companion object {
        private const val LOGGING_TAG = "ItchLibraryParser"
        
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        private val thumbnailCssPattern = Regex("""background-image:\s+url\('([^']*)'\)""")
        
        const val PAGE_SIZE = 50

        /**
         * @return null if user is not logged in and has no access, otherwise a list of items (if size == [PAGE_SIZE], should request next page)
         */
        suspend fun parsePage(page: Int): List<ItchLibraryItem>? = withContext(Dispatchers.IO) {
            val request = Request.Builder().run {
                url("https://itch.io/my-purchases?format=json&page=$page")

                addHeader("Cookie", CookieManager.getInstance().getCookie("https://itch.io"))
                get()
                build()
            }
            val result: String =
                Mitch.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful)
                        throw IOException("Unexpected code $response")

                    if (response.isRedirect)
                        return@withContext null

                    response.body?.string() ?: throw IOException("Expected response body, got null")
                }

            val resultJson = try {
                JSONObject(result)
            } catch (e: JSONException) {
                //Invalid JSON == we got redirected to login page
                return@withContext null
            }
            
            val itemCount = resultJson.getInt("num_items")
            val items = ArrayList<ItchLibraryItem>(itemCount)

            val document = Jsoup.parse(resultJson.getString("content"))
            for (gameDiv in document.getElementsByClass("game_cell")) {
                val purchaseDate = gameDiv.getElementsByClass("date_header").firstOrNull()
                    ?.getElementsByTag("span")?.text()
                
                val thumbnailLink = gameDiv.getElementsByClass("thumb_link").first()
                val downloadUrl = thumbnailLink.attr("href")
                val thumbnailUrl = thumbnailCssPattern.find(thumbnailLink.child(0).attr("style"))
                        ?.groupValues?.get(1)
                val title = gameDiv.getElementsByClass("game_title").first().text()
                val description = gameDiv.getElementsByClass("game_text").attr("title")
                val author = gameDiv.getElementsByClass("game_author").first().text()
                val isAndroid = gameDiv.getElementsByClass("icon-android").isNotEmpty()

                items.add(ItchLibraryItem(
                    purchaseDate = if (purchaseDate.isNullOrEmpty()) null else purchaseDate,
                    downloadUrl = downloadUrl,
                    thumbnailUrl = thumbnailUrl,
                    title = title,
                    author = author,
                    description = description,
                    isAndroid = isAndroid
                ))
            }

            return@withContext items
        }
    }
}