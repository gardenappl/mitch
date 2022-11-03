package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
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
import garden.appl.mitch.databinding.ActivityGameBinding

class GameActivity : MitchActivity() {
    companion object {
        private const val LOGGING_TAG = "GameActivity"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_IS_OFFLINE = "IS_OFFLINE"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"
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
        bindService(foregroundServiceIntent, connection, 0)

        showLaunchDialog(this, intent.data.toString())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.data?.toString() != this.intent.data.toString())
            showLaunchDialog(this, intent?.data.toString())
    }

    override fun onResume() {
        super.onResume()
        webView.resumeTimers()
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
//        val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)
//        val unencodedHtml = """
//            <html>
//                <body>
//                    <h1>Hello</h1>
//                    <p>Umm uhh <a href="$url">Click here</a> <a href="#">or here</a></p>
//                </body>
//            </html>
//        """.trimIndent()
//        val encodedHtml = Base64.encodeToString(unencodedHtml.toByteArray(), Base64.NO_PADDING)
//        webView.loadData(encodedHtml, "text/html", "base64")
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        stopService(Intent(this, GameForegroundService::class.java))
    }

    override fun onPause() {
        super.onPause()
//        webView.onPause()
        webView.pauseTimers()
        CookieManager.getInstance().flush()
        runBlocking(Dispatchers.IO) {
            Mitch.webGameCache.flush()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.settings.javaScriptEnabled = false
        webView.destroy()
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
        val intent = Intent(Intent.ACTION_VIEW, intent.data, applicationContext,
            GameActivity::class.java)
        intent.putExtra(EXTRA_GAME_ID, intent.getIntExtra(EXTRA_GAME_ID, -1))
        intent.putExtra(EXTRA_IS_OFFLINE, intent.getBooleanExtra(EXTRA_IS_OFFLINE, false))
        return intent
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