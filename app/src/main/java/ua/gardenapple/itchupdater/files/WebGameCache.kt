package ua.gardenapple.itchupdater.files

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.internal.cacheGet
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebGameCache(context: Context) {
    companion object {
        private const val LOGGING_TAG = "WebGameCache"
    }

    private val cacheDir = File(context.cacheDir, "webgames")
    private val cacheHttpClients = HashMap<Int, OkHttpClient>()

    suspend fun request(
        context: Context,
        game: Game,
        request: WebResourceRequest,
        isOfflineWebGame: Boolean
    ): WebResourceResponse? = withContext(Dispatchers.IO) {
        val updateWebCache = if (isOfflineWebGame) {
            Log.d(LOGGING_TAG, "Currently in offline mode")
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            Log.d(LOGGING_TAG, sharedPrefs.getString(PREF_WEB_CACHE_UPDATE, PreferenceWebCacheUpdate.NEVER)!!)
            when (sharedPrefs.getString(PREF_WEB_CACHE_UPDATE, PreferenceWebCacheUpdate.NEVER)) {
                PreferenceWebCacheUpdate.NEVER ->
                    false
                PreferenceWebCacheUpdate.UNMETERED ->
                    Utils.isNetworkConnected(context, requireUnmetered = true)
                else ->
                    Utils.isNetworkConnected(context)
            }
        } else {
            true
        }
        Log.d(LOGGING_TAG, "Update web cache: $updateWebCache")

        val url = request.url.toString()
        val httpRequest = Request.Builder().run {
            url(url)
            CookieManager.getInstance()?.getCookie(url)?.let { cookie ->
                addHeader("Cookie", cookie)
            }
            get()
            build()
        }
        val httpClient = getOkHttpClientForGame(game)
        Log.d(LOGGING_TAG, "current cache in ${httpClient.cache?.directory}")

        request(httpClient, httpRequest, forceCache = !updateWebCache)
    }

    private suspend fun request(httpClient: OkHttpClient, request: Request, forceCache: Boolean): WebResourceResponse? {
        val httpRequest = request.newBuilder().run {
            if (forceCache)
                cacheControl(CacheControl.FORCE_CACHE)
            else
                cacheControl(CacheControl.Builder().run {
                    minFresh(10, TimeUnit.MINUTES)
                    build()
                })
            build()
        }

        val response = suspendCancellableCoroutine<WebResourceResponse?> { continuation ->
            httpClient.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (e is UnknownHostException)
                        continuation.resume(null)
                    else
                        continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body
                    continuation.invokeOnCancellation {
                        response.close()
                    }
                    if (!response.isSuccessful) {
                        continuation.resume(null)
                        return
                    }
                    val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" }

                    continuation.resume(
                        WebResourceResponse(
                            mimeType,
                            body.contentType()?.charset()?.name(),
                            response.code,
                            response.message.ifEmpty { "(empty)" },
                            response.headers.toMultimap().mapValues { kvp ->
                                kvp.value.joinToString(separator = ",")
                            },
                            body.byteStream()
                        )
                    )
                }
            })
        }
        Log.d(LOGGING_TAG, "response code for ${request.url}: ${response?.statusCode}")
        if (response != null) {
            for (header in response.responseHeaders) {
                Log.d(LOGGING_TAG, "${header.key}: ${header.value}")
            }
        }
        return if (response == null && !forceCache)
            request(httpClient, request, forceCache = true)
        else
            response
    }

    private fun getOkHttpClientForGame(game: Game): OkHttpClient {
        return cacheHttpClients.getOrPut(game.gameId) { ->
            Log.d(LOGGING_TAG, "making new client for $game")
            Mitch.httpClient.newBuilder().let {
                val gameCacheDir = getCacheDir(game)
                gameCacheDir.mkdirs()
                it.cache(Cache(gameCacheDir, Long.MAX_VALUE))
                it.build()
            }
        }
    }

    private fun getCacheDir(game: Game): File = File(cacheDir, game.gameId.toString())

    suspend fun deleteCacheForGame(context: Context, game: Game) {
        withContext(Dispatchers.IO) {
            cacheHttpClients.remove(game.gameId)?.run {
                cache?.delete()
            } ?: getCacheDir(game).deleteRecursively()
            Log.d(LOGGING_TAG, "deleted ${getCacheDir(game)}")
        }

        val db = AppDatabase.getDatabase(context)
        db.gameDao.upsert(game.copy(
            webEntryPoint = null
        ))
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        for (httpClient in cacheHttpClients.values) {
            httpClient.cache?.flush()
        }
    }
}