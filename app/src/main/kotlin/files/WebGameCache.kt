package garden.appl.mitch.files

import android.content.Context
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Toast
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
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
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
        context: Context,
        game: Game,
        request: WebResourceRequest,
        isOfflineWebGame: Boolean
    ): WebResourceResponse? = withContext(Dispatchers.IO) {
        val updateWebCache = if (isOfflineWebGame) {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
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
        return if (response == null && !forceCache)
            request(httpClient, request, forceCache = true)
        else
            response
    }

    private fun getOkHttpClientForGame(game: Game): OkHttpClient {
        return cacheHttpClients.getOrPut(game.gameId) { ->
            val cacheDir = getCacheDir(game.gameId)
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

    suspend fun makeGameWebCached(context: Context, gameId: Int): Game {
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