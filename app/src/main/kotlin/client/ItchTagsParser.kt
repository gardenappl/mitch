package garden.appl.mitch.client

import garden.appl.mitch.Mitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object ItchTagsParser {
    private const val LOGGING_TAG = "ItchTagParser"

    suspend fun parseTags(classification: ItchTag.Classification): List<ItchTag> {
        val result = withContext(Dispatchers.IO) {
            val request = Request.Builder().run {
                url("https://itch.io/tags.json?classification=${classification.slug}&format=browse")
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
        }

        val tags = mutableListOf<ItchTag>()
        val tagsJson = JSONObject(result).getJSONArray("tags")
        for (i in 0 until tagsJson.length()) {
            val tagJson = tagsJson.getJSONObject(i)

            val facets = tagJson.getJSONObject("facets")
            if (!facets.has("tag"))
                continue

            tags.add(ItchTag(
                name = tagJson.getString("name"),
                url = tagJson.getString("url"),
                tag = facets.getString("tag"),
                primary = tagJson.optBoolean("primary")
            ))
        }
        return tags
    }
}