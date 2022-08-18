package garden.appl.mitch.files

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import garden.appl.mitch.*
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
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
                val gameCacheDir = getCacheDir(game.gameId)
                gameCacheDir.mkdirs()
                it.cache(Cache(gameCacheDir, Long.MAX_VALUE))
                it.build()
            }
        }
    }

    private fun getCacheDir(gameId: Int): File = File(cacheDir, gameId.toString())

    suspend fun makeGameWebCached(context: Context, gameId: Int): Game {
        val db = AppDatabase.getDatabase(context)
        val install = db.installDao.getWebInstallationForGame(gameId)
        Log.d(LOGGING_TAG, "Current web install is $install")

        val newInstall = Installation(
            internalId = install?.internalId ?: 0,
            gameId = gameId,
            uploadId = Installation.WEB_UPLOAD_ID,
            availableUploadIds = null,
            status = Installation.STATUS_WEB_CACHED,
            uploadName = Installation.WEB_UPLOAD_NAME,
            fileSize = Installation.WEB_FILE_SIZE
        )
        db.installDao.upsert(newInstall)
        return db.gameDao.getGameById(gameId)!!
    }

    suspend fun isGameWebCached(context: Context, gameId: Int): Boolean {
        val db = AppDatabase.getDatabase(context)
        return db.installDao.getWebInstallationForGame(gameId) != null
    }

    suspend fun deleteCacheForGame(context: Context, gameId: Int) {
        withContext(Dispatchers.IO) {
            cacheHttpClients.remove(gameId)?.run {
                cache?.delete()
            } ?: getCacheDir(gameId).deleteRecursively()
            Log.d(LOGGING_TAG, "deleted ${getCacheDir(gameId)}")
        }

        val db = AppDatabase.getDatabase(context)
        db.installDao.deleteWebInstallationForGame(gameId)
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        for (httpClient in cacheHttpClients) {
            httpClient.value.cache?.flush()
            Log.d(LOGGING_TAG, "flushed for ID ${httpClient.key}")
        }
    }

    suspend fun cleanCaches(db: AppDatabase) {
        val installs = db.installDao.getWebInstallationsSync()
        val dirs = cacheDir.listFiles() ?: return

        Log.d(LOGGING_TAG, "Cleaning up...")
        for (cacheDir in dirs) {
            try {
                val cacheGameId = Integer.parseInt(cacheDir.name)
                if (installs.find { install -> install.gameId == cacheGameId } == null) {
                    Log.d(LOGGING_TAG, "Cleaning up $cacheGameId")
                    cacheDir.deleteRecursively()
                }
            } catch (e: NumberFormatException) {
                //Directory name is not a number; assume it's not a game cache directory
                continue
            }
        }
        Log.d(LOGGING_TAG, "Cleaning up done.")
    }
}