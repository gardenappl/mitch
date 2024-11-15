package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.Mitch
import garden.appl.mitch.PREF_WEB_CACHE_ENABLE
import garden.appl.mitch.PREF_WEB_CACHE_UPDATE
import garden.appl.mitch.PreferenceWebCacheEnable
import garden.appl.mitch.PreferenceWebCacheUpdate
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.databinding.ActivityGameBinding
import garden.appl.mitch.files.Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

/**
 * Data URI is unused, but currently the hwcdn URL is supplied.
 */
class GameActivity : MitchActivity(), CoroutineScope by MainScope() {
    companion object {
        private const val LOGGING_TAG = "GameActivity"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_LAUNCHED_FROM_INSTALL = "IS_OFFLINE"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"

        fun getShortcutId(gameId: Int) = "web_game/${gameId}"

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
                putExtra(EXTRA_LAUNCHED_FROM_INSTALL, true)
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
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private var isOfflineMode: Boolean = false
    private var isCaching: Boolean = false

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

        val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { fileChooserCallback?.onReceiveValue(arrayOf(it)) }
            fileChooserCallback = null
        }
        val openMultipleDocumentsLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            fileChooserCallback?.onReceiveValue(uris.toTypedArray())
            fileChooserCallback = null
        }
        chromeClient = GameChromeClient(openDocumentLauncher, openMultipleDocumentsLauncher)

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

        webView.setBackgroundColor(Utils.getColor(this, R.color.colorAccent))

        webView.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
            Mitch.externalFileManager.requestPermissionIfNeeded(this) {
                val fileName = Utils.guessFileName(url, contentDisposition, mimeType)
                Log.d(LOGGING_TAG, "Guessed file name: $fileName")
                this.launch {
                    Downloader.requestDownload(
                        this@GameActivity, url,
                        install = null,
                        fileName = fileName,
                        contentLength = contentLength,
                        downloadDir = null,
                        tempDownloadDir = true,
                        installer = null
                    )
                }
            }
        }

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
        if (bundle != null) {
            webView.restoreState(bundle)
        } else {
            launch {
                showLaunchDialog()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getIntExtra(EXTRA_GAME_ID, -1)
            != this.intent?.getIntExtra(EXTRA_GAME_ID, -2)) {

            launch {
                showLaunchDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        webView.resumeTimers()
        webView.onResume()
    }

    private suspend fun showLaunchDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)
        val installedOffline = Mitch.webGameCache.isGameWebCached(this, gameId)

        val webCacheEnabled = try {
            prefs.getString(PREF_WEB_CACHE_ENABLE, PreferenceWebCacheEnable.DEFAULT)
                ?.toBooleanStrictOrNull()
        } catch (_: ClassCastException) {
            if (prefs.getBoolean("mitch.web_cache_dialog_hide", false))
                false
            else
                null
        }

        if (installedOffline || intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_INSTALL, false)) {
            afterDialogShown(installedOffline, true)
            return
        } else if (webCacheEnabled != null) {
            afterDialogShown(installedOffline, webCacheEnabled)
            return
        }
        val dialog = AlertDialog.Builder(this).apply {
            setTitle(R.string.dialog_web_cache_info_title)
            setMessage(R.string.dialog_web_cache_info)

            val hideCheckBox = CheckBox(context).apply {
                setText(R.string.dialog_dont_ask_again)
            }

            setView(FrameLayout(context).apply {
                addView(hideCheckBox, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(50, 25, 50, 0)
                })
            })

            setPositiveButton(R.string.dialog_yes) { _, _ ->
                prefs.edit(commit = true) {
                    if (hideCheckBox.isChecked) {
                        putString(PREF_WEB_CACHE_ENABLE, PreferenceWebCacheEnable.ALWAYS)
                    }
                }
                launch { afterDialogShown(installedOffline, true) }
            }
            setNegativeButton(R.string.dialog_no) { _, _ ->
                prefs.edit(commit = true) {
                    if (hideCheckBox.isChecked) {
                        putString(PREF_WEB_CACHE_ENABLE, PreferenceWebCacheEnable.NEVER)
                    }
                }
                launch { afterDialogShown(installedOffline, false) }
            }
            setCancelable(true)
            setOnCancelListener {
                launch { afterDialogShown(installedOffline, false) }
            }

            create()
        }
        dialog.show()
    }

    private suspend fun afterDialogShown(installedOffline: Boolean, shouldCache: Boolean) {
        val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)

        Log.d(LOGGING_TAG, "Running $gameId, installed offline: $installedOffline, should cache: $shouldCache")

        if (shouldCache && !installedOffline) {
            Toast.makeText(this, R.string.popup_web_game_cached, Toast.LENGTH_LONG)
                .show()
            Mitch.webGameCache.makeGameWebCached(this, gameId)
            this.isOfflineMode = false
        } else if (installedOffline) {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
            when (sharedPrefs.getString(PREF_WEB_CACHE_UPDATE, PreferenceWebCacheUpdate.NEVER)) {
                PreferenceWebCacheUpdate.NEVER ->
                    this.isOfflineMode = true
                PreferenceWebCacheUpdate.UNMETERED ->
                    this.isOfflineMode = !Utils.isNetworkConnected(this, requireUnmetered = true)
                else ->
                    this.isOfflineMode = !Utils.isNetworkConnected(this)
            }
        } else {
            this.isOfflineMode = false
        }

        if (this.isOfflineMode)
            Toast.makeText(this, R.string.popup_web_game_offline_mode, Toast.LENGTH_LONG)
                .show()
        this.isCaching = shouldCache

        val db = AppDatabase.getDatabase(this)
        val game = tryFixBackwardsCompatGame(db.gameDao.getGameById(gameId)!!, this)

        loadGame(game)
        val shortcut = makeShortcut(game, this@GameActivity)
        ShortcutManagerCompat.pushDynamicShortcut(this@GameActivity, shortcut)
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
                        /* background-color: #FA5C5C; */
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
            val dialog = AlertDialog.Builder(this).apply {
                setMessage(R.string.popup_web_game_exit)
                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    super.onBackPressed()
                }
                setNegativeButton(R.string.dialog_no) { _, _ -> /* NO-OP */ }
                setCancelable(true)
            }
            dialog.show()
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
        newIntent.putExtra(EXTRA_LAUNCHED_FROM_INSTALL, intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_INSTALL, false))
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
            val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)

            if (!this@GameActivity.isCaching) {
//                Utils.logDebug(LOGGING_TAG, "GET ${request.url}, not intercepting...")
                return@runBlocking null
            }
//            Utils.logDebug(LOGGING_TAG, "GET ${request.url}, ${request.requestHeaders.entries.joinToString()}")

            Mitch.webGameCache.request(applicationContext, gameId, request,
                this@GameActivity.isOfflineMode)
        }
    }

    inner class GameChromeClient(
        openDocumentLauncher: ActivityResultLauncher<Array<String>>,
        openMultipleDocumentsLauncher: ActivityResultLauncher<Array<String>>
    ) : MitchWebChromeClient(openDocumentLauncher, openMultipleDocumentsLauncher) {
        private var customView: View? = null

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            webView.visibility = View.GONE
            binding.root.addView(view)
        }

        override fun onHideCustomView() {
            webView.visibility = View.VISIBLE
            customView?.let { binding.root.removeView(it) }
        }

        override fun setFileChooserCallback(callback: ValueCallback<Array<Uri>>) {
            this@GameActivity.fileChooserCallback = callback
        }
    }
}