package ua.gardenapple.itchupdater.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.databinding.ActivityGameBinding
import java.io.ByteArrayInputStream
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
    private lateinit var chromeClient: WebChromeClient

    private var originalUiVisibility: Int = View.VISIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.gameWebView
    }

    override fun onStart() {
        super.onStart()

        if (intent.action == Intent.ACTION_VIEW) {
            webView.loadUrl(intent.data.toString())
        }

        chromeClient = WebChromeClient()

        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setAppCacheEnabled(true)
        webView.settings.setAppCachePath(File(filesDir, "html5-app-cache").path)
        webView.settings.databaseEnabled = true

        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = GameWebViewClient()
        webView.webChromeClient = chromeClient


        val foregroundServiceIntent = Intent(this, WebViewForegroundService::class.java)
        foregroundServiceIntent.putExtra(WebViewForegroundService.EXTRA_ORIGINAL_INTENT, intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(foregroundServiceIntent)
        } else {
            startService(foregroundServiceIntent)
        }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, WebViewForegroundService::class.java))
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
        val githubLoginPathRegex = Regex("""/?(login|sessions)(/.*)?""")

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (ItchWebsiteUtils.isItchWebPage(request.url)) {
                return false
            } else if (request.url.host == "github.com"
                && request.url.path?.matches(githubLoginPathRegex) == true
            ) {
                return false
            } else {
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
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val isOffline = this@GameActivity.intent.getBooleanExtra(EXTRA_IS_OFFLINE, false)

            runOnUiThread {
                showWebGameLaunchDialog(applicationContext)
            }

            return runBlocking(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@GameActivity)
                val game =
                    db.gameDao.getGameById(this@GameActivity.intent.getIntExtra(EXTRA_GAME_ID, 0))!!
                Log.d(LOGGING_TAG, "got $game")
                Mitch.webGameCache.request(applicationContext, game, request, isOffline)
            }
        }

        private fun showWebGameLaunchDialog(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            if (!prefs.getBoolean(PREF_WEB_CACHE_DIALOG_HIDE, false)) {
                val dialog = AlertDialog.Builder(context).apply {
                    setTitle(R.string.dialog_web_cache_info_title)
                    setMessage(R.string.dialog_web_cache_info)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        prefs.edit().run {
                            putBoolean(PREF_WEB_CACHE_ENABLE, true)
                            putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                            apply()
                        }
                    }
                    setNegativeButton(android.R.string.cancel) { _, _ ->
                        prefs.edit().run {
                            putBoolean(PREF_WEB_CACHE_ENABLE, false)
                            putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
                            apply()
                        }
                    }
                    setCancelable(true)

                    create()
                }
                dialog.show()
            }
        }
    }
}