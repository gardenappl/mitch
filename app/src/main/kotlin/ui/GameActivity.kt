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
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.databinding.ActivityGameBinding
import kotlinx.coroutines.*
import java.io.IOException
import java.net.SocketTimeoutException

class GameActivity : MitchActivity(), CoroutineScope by MainScope() {
    companion object {
        private const val LOGGING_TAG = "GameActivity"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_IS_OFFLINE = "IS_OFFLINE"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"

        fun getShortcutId(gameId: Int) = "web_game/${gameId}"
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


        val foregroundServiceIntent = Intent(this, GameForegroundService::class.java)
        foregroundServiceIntent.putExtra(GameForegroundService.EXTRA_ORIGINAL_INTENT, intent)

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
            showLaunchDialog(this, intent.data.toString())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.data?.toString() != this.intent.data.toString())
            showLaunchDialog(this, intent?.data.toString())
    }

    override fun onResume() {
        super.onResume()
//        webView.resumeTimers()
        webView.onResume()
    }

    private fun showLaunchDialog(context: Context, url: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (prefs.getBoolean(PREF_WEB_CACHE_DIALOG_HIDE, false)) {
            afterDialogShown(url)
        } else {
            val dialog = AlertDialog.Builder(context).apply {
                setTitle(R.string.dialog_web_cache_info_title)
                setMessage(R.string.dialog_web_cache_info)
                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    prefs.edit(commit = true) {
                        putBoolean(PREF_WEB_CACHE_ENABLE, true)
                        putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                    }
                    afterDialogShown(url)
                }
                setNegativeButton(R.string.dialog_no) { _, _ ->
                    prefs.edit(commit = true) {
                        putBoolean(PREF_WEB_CACHE_ENABLE, false)
                        putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                    }
                    afterDialogShown(url)
                }
                setCancelable(true)
                setOnCancelListener {
                    afterDialogShown(url)
                }

                create()
            }
            dialog.show()
        }
    }

    private fun afterDialogShown(url: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (prefs.getBoolean(PREF_WEB_CACHE_ENABLE, true)
            && !intent.getBooleanExtra(EXTRA_IS_OFFLINE, false)) {

            Toast.makeText(this, R.string.popup_web_game_cached, Toast.LENGTH_LONG).show()
        }
        webView.loadUrl(url)

        val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)
        Log.d(LOGGING_TAG, "Running $gameId")
        if (gameId != -1) {
            launch(Dispatchers.IO) {
                val game = AppDatabase.getDatabase(this@GameActivity).gameDao.getGameById(gameId)
                    ?: return@launch

                val downloadedBitmap = try {
                    Glide.with(this@GameActivity).asBitmap().run {
                        load(game.thumbnailUrl)
                        submit()
                    }.get()
                } catch (e: IOException) {
                    return@launch
                }
                val faviconBitmap =
                    Bitmap.createScaledBitmap(downloadedBitmap, 108, 108, false)

                val shortcut =
                    ShortcutInfoCompat.Builder(this@GameActivity, getShortcutId(gameId)).run {
                        setShortLabel(game.name)
                        setIcon(IconCompat.createWithAdaptiveBitmap(faviconBitmap))
                        setIntent(makeIntentForRestart())
                        build()
                    }

                ShortcutManagerCompat.pushDynamicShortcut(this@GameActivity, shortcut)
            }
        }
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
            val isOffline = intent.getBooleanExtra(EXTRA_IS_OFFLINE, false)
            val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)

            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this@GameActivity)

            if (!sharedPrefs.getBoolean(PREF_WEB_CACHE_ENABLE, false)
                    && !Mitch.webGameCache.isGameWebCached(this@GameActivity, gameId)) {
                return@runBlocking null
            }

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