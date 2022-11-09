package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import garden.appl.mitch.*
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.databinding.ActivityGameBinding
import kotlinx.coroutines.*
import okhttp3.internal.cacheGet
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Data URI is unused, but currently the hwcdn URL is supplied.
 */
class GameActivity : MitchActivity(), CoroutineScope by MainScope() {
    companion object {
        private const val LOGGING_TAG = "GameActivity"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_IS_OFFLINE = "IS_OFFLINE"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"

        fun getShortcutId(gameId: Int) = "web_game/${gameId}"

        /**
         * @return for backwards compat reasons, returns false if failed to get thumbnail
         */
        suspend fun makeShortcut(game: Game, context: Context): ShortcutInfoCompat {
            val game = tryFixBackwardsCompatGame(game, context)
            val faviconBitmap = game.faviconUrl?.let { url ->
                withContext(Dispatchers.IO) {
                    val bitmap = try {
                        Glide.with(context).asBitmap().run {
                            load(url)
                            submit()
                        }.get()
                    } catch (e: ExecutionException) {
                        Log.e(LOGGING_TAG, "no thumbnail: ${e.cause}")
                        return@withContext null
                    }
                    Bitmap.createScaledBitmap(bitmap, 128, 128, false)
                }
            }
            Log.d(LOGGING_TAG, "Game: $game")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(game.webEntryPoint),
                context, GameActivity::class.java).apply {

                putExtra(EXTRA_GAME_ID, game.gameId)
                putExtra(EXTRA_IS_OFFLINE, true)
            }
            val shortcutId = getShortcutId(game.gameId)
            return ShortcutInfoCompat.Builder(context, shortcutId).run {
                setShortLabel(game.name)
                if (faviconBitmap != null)
                    setIcon(IconCompat.createWithBitmap(faviconBitmap))
                setIntent(intent)
                build()
            }
        }

        /**
         * @return possibly an updated instance of [Game]
         */
        private suspend fun tryFixBackwardsCompatGame(game: Game, context: Context): Game {
            if (game.webIframe != null)
                return game

            Log.d(LOGGING_TAG, "getting iframe and favicon as backwards compat")
            try {
                val doc = ItchWebsiteUtils.fetchAndParse(game.storeUrl)
                val parsedGame = ItchWebsiteParser.getGameInfoForStorePage(doc, game.storeUrl)!!
                val newGame = game.copy(
                    webIframe = parsedGame.webIframe,
                    faviconUrl = parsedGame.faviconUrl
                )
                val db = AppDatabase.getDatabase(context)
                db.gameDao.upsert(newGame)
                return newGame
            } catch (e: Exception) {
                return game
            }
        }
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var webView: WebView
    private lateinit var chromeClient: GameChromeClient

    private val connection = object : ServiceConnection {
        //NO-OP, we bind to a foreground service so that Android does not kill us
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {}
        override fun onServiceDisconnected(p0: ComponentName?) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        binding.root.keepScreenOn = true
        webView = binding.gameWebView

        chromeClient = GameChromeClient()

        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
//        webView.settings.setAppCacheEnabled(true)
//        webView.settings.setAppCachePath(File(filesDir, "html5-app-cache").path)
        webView.settings.databaseEnabled = true

        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = GameWebViewClient()
        webView.webChromeClient = chromeClient

        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false


        val gameId = intent?.getIntExtra(EXTRA_GAME_ID, -1) ?: -1

        val foregroundServiceIntent = Intent(this, GameForegroundService::class.java)
        foregroundServiceIntent.putExtra(GameForegroundService.EXTRA_ORIGINAL_INTENT, intent)
        foregroundServiceIntent.putExtra(GameForegroundService.EXTRA_GAME_ID, gameId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(foregroundServiceIntent)
        } else {
            startService(foregroundServiceIntent)
        }
        bindService(foregroundServiceIntent, connection, 0)


        val bundle = savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY)
        if (bundle != null)
            webView.restoreState(bundle)
        else
            showLaunchDialog(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getIntExtra(EXTRA_GAME_ID, -1)
            != this.intent?.getIntExtra(EXTRA_GAME_ID, -2)) {

            showLaunchDialog(this)
        }
    }

    override fun onResume() {
        super.onResume()
//        webView.resumeTimers()
        webView.onResume()
    }

    private fun showLaunchDialog(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (prefs.getBoolean(PREF_WEB_CACHE_DIALOG_HIDE, false)) {
            afterDialogShown()
        } else {
            val dialog = AlertDialog.Builder(context).apply {
                setTitle(R.string.dialog_web_cache_info_title)
                setMessage(R.string.dialog_web_cache_info)
                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    prefs.edit(commit = true) {
                        putBoolean(PREF_WEB_CACHE_ENABLE, true)
                        putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                    }
                    afterDialogShown()
                }
                setNegativeButton(R.string.dialog_no) { _, _ ->
                    prefs.edit(commit = true) {
                        putBoolean(PREF_WEB_CACHE_ENABLE, false)
                        putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                    }
                    afterDialogShown()
                }
                setCancelable(true)
                setOnCancelListener {
                    afterDialogShown()
                }

                create()
            }
            dialog.show()
        }
    }

    private fun afterDialogShown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)
        val isOffline = intent.getBooleanExtra(EXTRA_IS_OFFLINE, false)

        Log.d(LOGGING_TAG, "Running $gameId, offline: $isOffline")
        if (gameId != -1) {
            launch {
                val db = AppDatabase.getDatabase(this@GameActivity)

                val webInstall = db.installDao.getWebInstallationForGame(gameId)
                if (webInstall == null) {
                    if (isOffline) {
                        Toast.makeText(
                            this@GameActivity,
                            R.string.popup_web_game_deleted_cannot_launch,
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@launch
                    } else if (prefs.getBoolean(PREF_WEB_CACHE_ENABLE, true)) {
                        Toast.makeText(
                            this@GameActivity,
                            R.string.popup_web_game_cached,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                val gameBackwardsCompat = db.gameDao.getGameById(gameId)!!
                val game = tryFixBackwardsCompatGame(gameBackwardsCompat, this@GameActivity)

                loadGame(game)
                val shortcut = makeShortcut(game, this@GameActivity)
                ShortcutManagerCompat.pushDynamicShortcut(this@GameActivity, shortcut)
            }
        }
    }

    private fun loadGame(game: Game) {
        if (game.webIframe == null) {
            // backwards compat
            webView.loadUrl(game.webEntryPoint!!)
            return
        }

        val html = """<html>
            <head>
                <style type="text/css">
                    html {
                        overflow: auto;
                    }
                    
                    html, body, div, iframe {
                        margin: 0px; 
                        padding: 0px; 
                        height: 100%; 
                        border: none;
                    }
                    iframe {
                        display: block; 
                        width: 100%; 
                        border: none; 
                        overflow-y: auto; 
                        overflow-x: hidden;
                    }
                </style>
            </head>
            <body>${game.webIframe}</body>
        </html>""".trimIndent()
        webView.loadDataWithBaseURL(game.storeUrl, html, "text/html", "UTF-8", null)
//        webView.loadDataWithBaseURL(game.webEntryPoint!!, html, "text/html", "UTF-8", null)
    }

    override fun onPause() {
        super.onPause()

        webView.onPause()
        CookieManager.getInstance().flush()
        runBlocking {
            Mitch.webGameCache.flush()
        }
    }

    override fun onDestroy() {
        unbindService(connection)
        stopService(Intent(this, GameForegroundService::class.java))
        webView.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webViewState = Bundle()
        webView.saveState(webViewState)
        outState.putBundle(WEB_VIEW_STATE_KEY, webViewState)

        super.onSaveInstanceState(outState)
    }

    override fun makeIntentForRestart(): Intent {
        val newIntent = Intent(Intent.ACTION_VIEW, intent.data, applicationContext,
            GameActivity::class.java)
        newIntent.putExtra(EXTRA_GAME_ID, intent.getIntExtra(EXTRA_GAME_ID, -1))
        newIntent.putExtra(EXTRA_IS_OFFLINE, intent.getBooleanExtra(EXTRA_IS_OFFLINE, false))
        return newIntent
    }

    inner class GameWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (ItchWebsiteUtils.isItchWebPageOrCDN(request.url)) {
                return false
            }
            val intent = Intent(Intent.ACTION_VIEW, request.url)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Log.i(LOGGING_TAG, "No app found that can handle ${request.url}")
                Toast.makeText(
                    applicationContext,
                    resources.getString(R.string.popup_handler_app_not_found, request.url),
                    Toast.LENGTH_LONG
                ).show()
            }
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? = runBlocking(Dispatchers.IO) {
            if (request.url.scheme?.startsWith("http") != true)
                return@runBlocking null
            if (!request.method.equals("GET", ignoreCase = true))
                return@runBlocking null
            val isOffline = intent.getBooleanExtra(EXTRA_IS_OFFLINE, false)
            val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)

            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this@GameActivity)

            if (!sharedPrefs.getBoolean(PREF_WEB_CACHE_ENABLE, false)
                    && !Mitch.webGameCache.isGameWebCached(this@GameActivity, gameId)) {
                return@runBlocking null
            }
            Utils.logDebug(LOGGING_TAG, "GET ${request.url}, ${request.requestHeaders.entries.joinToString()}")
            val game = Mitch.webGameCache.makeGameWebCached(this@GameActivity, gameId)

            Mitch.webGameCache.request(applicationContext, game, request, isOffline)
        }
    }

    inner class GameChromeClient : WebChromeClient() {
        private var customView: View? = null

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            webView.visibility = View.GONE
            binding.root.addView(view)
        }

        override fun onHideCustomView() {
            webView.visibility = View.VISIBLE
            customView?.let { binding.root.removeView(it) }
        }
    }
}