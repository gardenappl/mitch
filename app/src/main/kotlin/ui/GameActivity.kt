package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import garden.appl.mitch.*
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.databinding.ActivityGameBinding
import garden.appl.mitch.files.WebGameCache
import java.io.File

class GameActivity : Activity() {
    companion object {
        private const val LOGGING_TAG = "GameActivity"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_IS_OFFLINE = "IS_OFFLINE"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var webView: WebView
    private lateinit var chromeClient: GameChromeClient

    private var originalUiVisibility: Int = View.VISIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.gameWebView
    }

    override fun onStart() {
        super.onStart()

        if (intent.action != Intent.ACTION_VIEW)
            throw IllegalArgumentException("Only ACTION_VIEW is supported")

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

        showLaunchDialog(this, intent.data.toString())
    }

    private fun showLaunchDialog(context: Context, url: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (prefs.getBoolean(PREF_WEB_CACHE_DIALOG_HIDE, false)) {
            afterDialogShown(url)
        } else {
            val dialog = AlertDialog.Builder(context).apply {
                setTitle(R.string.dialog_web_cache_info_title)
                setMessage(R.string.dialog_web_cache_info)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit(commit = true) {
                        putBoolean(PREF_WEB_CACHE_ENABLE, true)
                        putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                    }
                    afterDialogShown(url)
                }
                setNegativeButton(android.R.string.cancel) { _, _ ->
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
        webView.loadUrl(url)
    }


    override fun onResume() {
        super.onResume()

        originalUiVisibility = binding.root.systemUiVisibility
        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        binding.root.keepScreenOn = true
    }

    override fun onPause() {
        super.onPause()
        binding.root.systemUiVisibility = originalUiVisibility
        CookieManager.getInstance().flush()
        runBlocking(Dispatchers.IO) {
            Mitch.webGameCache.flush()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.settings.javaScriptEnabled = false
        webView.destroy()
        stopService(Intent(this, GameForegroundService::class.java))
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            Log.d(LOGGING_TAG, "go back")
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

    inner class GameWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (ItchWebsiteUtils.isItchWebPage(request.url)) {
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
            val isOffline = intent.getBooleanExtra(EXTRA_IS_OFFLINE, false)
            val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)

            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this@GameActivity)

            if (!sharedPrefs.getBoolean(PREF_WEB_CACHE_ENABLE, false)
                    && !Mitch.webGameCache.isGameWebCached(this@GameActivity, gameId)) {
                return@runBlocking null
            }

            val game = Mitch.webGameCache.makeGameWebCached(this@GameActivity, gameId)
            Log.d(LOGGING_TAG, "got $game")

            Mitch.webGameCache.request(applicationContext, game, request, isOffline)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            view.evaluateJavascript(
                """
                    document.addEventListener("DOMContentLoaded", (event) => {
                        console.log("content loaded!");
                        let body = document.body;
                        body.addEventListener("click", (event) => {
                            console.log("fullscreen!");
                            body.requestFullscreen().catch(err => {
                                console.log("Error attempting to enable fullscreen mode:" + err.message + ' ' + err.name);
                            });
                        });
                    });
                """, null
            )
        }
    }

    inner class GameChromeClient : WebChromeClient() {
        private var customView: View? = null

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            Log.d(LOGGING_TAG, "Doing fullscreen...")
            webView.visibility = View.GONE
            binding.root.addView(view)
        }

        override fun onHideCustomView() {
            Log.d(LOGGING_TAG, "Doing NOT fullscreen...")
            webView.visibility = View.VISIBLE
            customView?.let { binding.root.removeView(it) }
        }
    }
}