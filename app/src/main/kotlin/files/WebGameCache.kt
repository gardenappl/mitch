package garden.appl.mitch.files

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import garden.appl.mitch.Mitch
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

    private val cacheDirLegacy = File(context.cacheDir, "webgames")
    private val cacheDir by lazy { context.getDir("webgames", Context.MODE_PRIVATE) }
    private val cacheHttpClients = HashMap<Int, OkHttpClient>()

    suspend fun request(
        gameId: Int,
        request: WebResourceRequest,
        isOfflineMode: Boolean
    ): WebResourceResponse? = withContext(Dispatchers.IO) {
        val url = request.url.toString()
//        Utils.logDebug(LOGGING_TAG, "$url, force cache?: $isOfflineMode")
        val httpRequest = Request.Builder().run {
            url(url)
            headers(request.requestHeaders.toHeaders())
            get()
            build()
        }
        val httpClient = getOkHttpClientForGame(gameId)

        request(httpClient, httpRequest, forceCache = isOfflineMode)
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

            // A bad workaround for https://todo.sr.ht/~gardenapple/mitch/31
            if (!request.url.host.endsWith(".hwcdn.net")
                && (request.url.encodedPath.endsWith(".ttf")
                    || request.url.encodedPath.endsWith(".woff")
                    || request.url.encodedPath.endsWith(".woff2"))) {

                header("Host", request.url.host)
                header("Sec-Fetch-Dest", "font")
                header("Sec-Fetch-Mode", "cors")
                header("Sec-Fetch-Site", "cross-site")
            }
            build()
        }
//        Utils.logDebug(LOGGING_TAG, "request: $httpRequest")

        val response = suspendCancellableCoroutine { continuation ->
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
//        Utils.logDebug(LOGGING_TAG, "response from ${httpRequest.url}: ${response?.responseHeaders}")
        return if (response == null && !forceCache)
            request(httpClient, request, forceCache = true)
        else
            response
    }

    private fun getOkHttpClientForGame(gameId: Int): OkHttpClient {
        return cacheHttpClients.getOrPut(gameId) { ->
            val cacheDir = getCacheDir(gameId)
            Utils.logDebug(LOGGING_TAG, "new client; cache dir: $cacheDir")
            Mitch.httpClient.newBuilder().let {
                it.cache(Cache(cacheDir, Long.MAX_VALUE))
                it.build()
            }
        }
    }

    private fun getCacheDir(gameId: Int): File {
        return migrateCacheDir(gameId, mkdir = true)
    }

    suspend fun makeGameWebCached(context: Context, gameId: Int) {
        val db = AppDatabase.getDatabase(context)
        val install = db.installDao.getWebInstallationForGame(gameId)

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
    }

    suspend fun isGameWebCached(context: Context, gameId: Int): Boolean {
        val db = AppDatabase.getDatabase(context)
        return db.installDao.getWebInstallationForGame(gameId) != null
    }

    suspend fun deleteCacheForGame(context: Context, gameId: Int) {
        withContext(Dispatchers.IO) {
            cacheHttpClients.remove(gameId)?.run {
                cache?.delete()
            }
            val cacheDir = getCacheDir(gameId)
            Utils.logDebug(LOGGING_TAG, "Deleting $cacheDir")
            cacheDir.deleteRecursively()
            Utils.logDebug(LOGGING_TAG, "exists? ${cacheDir.exists()}")
        }

        val db = AppDatabase.getDatabase(context)
        db.installDao.deleteWebInstallationForGame(gameId)
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        for (httpClient in cacheHttpClients) {
            httpClient.value.cache?.flush()
        }
    }

    suspend fun cleanCaches(db: AppDatabase) {
        val installs = db.installDao.getWebInstallationsSync()

        val dirsLegacy = cacheDirLegacy.listFiles()
        dirsLegacy?.forEach { dir ->
            migrateCacheDir(dir.name.toInt(), false)
        }
        cacheDirLegacy.deleteRecursively()

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

    private fun migrateCacheDir(gameId: Int, mkdir: Boolean): File {
        val dir = File(cacheDir, gameId.toString())
        val legacyDir = File(cacheDirLegacy, gameId.toString())

        try {
            if (legacyDir.renameTo(dir)) {
                Log.d(LOGGING_TAG, "Renamed from $legacyDir to $dir")
            } else {
                legacyDir.copyRecursively(dir)
                legacyDir.deleteRecursively()
                Log.d(LOGGING_TAG, "Moved from $legacyDir to $dir")
            }
            return dir
        } catch (e: NoSuchFileException) {
            //no-op
        } catch (e: Exception) {
            File(cacheDirLegacy, gameId.toString()).deleteRecursively()
            Log.d(LOGGING_TAG, "Failed to move $legacyDir, deleting and returning $dir")
        }

        if (mkdir)
            dir.mkdirs()
        return dir
    }
}