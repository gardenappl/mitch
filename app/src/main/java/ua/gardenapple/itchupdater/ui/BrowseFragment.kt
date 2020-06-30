package ua.gardenapple.itchupdater.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.ProgressBar
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.browse_fragment.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.ItchBrowseHandler
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.installer.DownloadRequester
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringReader
import java.net.URLEncoder


class BrowseFragment : Fragment(), CoroutineScope by MainScope() {

    companion object {
        const val LOGGING_TAG = "BrowseFragment"
        const val WEB_VIEW_STATE_KEY: String = "WebView"
    }

    private lateinit var chromeClient: MitchWebChromeClient
    lateinit var webView: MitchWebView
        private set

    var browseHandler: ItchBrowseHandler? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)
        val browseHandler = ItchBrowseHandler(context, this)
        this.browseHandler = browseHandler
    }

    override fun onDetach() {
        super.onDetach()
        browseHandler = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.browse_fragment, container, false)

        webView = view.findViewById(R.id.webView)
        chromeClient = MitchWebChromeClient()

        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setAppCacheEnabled(true)
        webView.settings.setAppCachePath(File(requireContext().filesDir, "html5-app-cache").path)
        webView.settings.databaseEnabled = true

        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = MitchWebViewClient()
        webView.webChromeClient = chromeClient

        webView.addJavascriptInterface(ItchJavaScriptInterface(this), "mitchCustomJS")

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            Log.d(LOGGING_TAG, "Requesting download...")
            requireContext().let {
                DownloadRequester.requestDownload(it, activity, url, contentDisposition, mimeType) {
                    downloadId: Long -> browseHandler!!.onDownloadStarted(downloadId)
                }
            }
        }

        //Set up FAB buttons
        //(colors don't matter too much as they will be set by processUI anyway)
        val speedDialView = (activity as MainActivity).speedDial
        val fabBgColor = ResourcesCompat.getColor(resources, R.color.colorPrimary, requireContext().theme)
        val fabFgColor = ResourcesCompat.getColor(resources, R.color.colorPrimaryDark, requireContext().theme)
        speedDialView.clearActionItems()
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_reload, R.drawable.ic_baseline_refresh_24)
            .setFabBackgroundColor(fabBgColor)
            .setLabelBackgroundColor(fabBgColor)
            .setFabImageTintColor(fabFgColor)
            .setLabelColor(fabFgColor)
            .setLabel(R.string.browser_reload)
            .create()
        )
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_search, R.drawable.ic_baseline_search_24)
            .setFabBackgroundColor(fabBgColor)
            .setLabelBackgroundColor(fabBgColor)
            .setFabImageTintColor(fabFgColor)
            .setLabelColor(fabFgColor)
            .setLabel(R.string.browser_search)
            .create()
        )
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_share, R.drawable.ic_baseline_share_24)
            .setFabBackgroundColor(fabBgColor)
            .setLabelBackgroundColor(fabBgColor)
            .setFabImageTintColor(fabFgColor)
            .setLabelColor(fabFgColor)
            .setLabel(R.string.browser_share)
            .create()
        )
        
        speedDialView.setOnActionSelectedListener { actionItem ->  
            when (actionItem.id) {
                R.id.browser_reload -> {
                    webView.reload()
                    speedDialView.close()
                    return@setOnActionSelectedListener true
                }
                R.id.browser_share -> {
                    ShareCompat.IntentBuilder.from(activity)
                        .setType("text/plain")
                        .setChooserTitle(R.string.browser_share)
                        .setText(webView.url)
                        .startChooser()
                    return@setOnActionSelectedListener true
                }
                R.id.browser_search -> {
                    speedDialView.close()

                    //Search dialog
                    val viewInflated: View = LayoutInflater.from(context)
                        .inflate(R.layout.dialog_search, getView() as ViewGroup?, false)

                    val input = viewInflated.findViewById<TextInputEditText>(R.id.input)

                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.browser_search)
                        .setView(viewInflated)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()

                            val searchQuery = URLEncoder.encode(input.text.toString(), "utf-8")
                            val url = "https://itch.io/search?q=$searchQuery"
                            webView.loadUrl(url)
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()

                    return@setOnActionSelectedListener true
                }
                else -> {
                    return@setOnActionSelectedListener false
                }
            }
        }

            
            
        //Loading a URL should be the last action so that it may call processUI
        if(savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY) != null) {
            Log.d(LOGGING_TAG, "Restoring WebView")
            webView.restoreState(savedInstanceState.getBundle(WEB_VIEW_STATE_KEY))
        } else {
            webView.loadUrl("https://itch.io/games/platform-android")
        }

        return view;
    }


    /**
     * @return true if the user can't go back in the web history
     */
    fun onBackPressed(): Boolean {
        if(chromeClient.customViewCallback != null) {
            Log.d(LOGGING_TAG, "Hiding custom view")
            chromeClient.customViewCallback?.onCustomViewHidden()
            return false
        } else {
            Log.d(LOGGING_TAG, "Custom view callback is null")
        }

        if(webView.canGoBack()) {
            webView.goBack()
            return false
        }
        return true
    }

    override fun onPause() {
        super.onPause()

        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    /**
     * Responsible for updating the UI as well as the local game database after a page has loaded.
     */
    private fun processUI() {
        webView.evaluateJavascript("""
            (function() {
                return "<html>"+document.getElementsByTagName("html")[0].innerHTML+"</html>";
            })();"""
        ) { result ->
            launch(Dispatchers.Default) {
                var resultParsed: String = ""

                val jsonReader = JsonReader(StringReader(result))
                jsonReader.isLenient = true
                jsonReader.use {
                    if(jsonReader.peek() == JsonToken.STRING)
                        resultParsed = jsonReader.nextString()
                }

//                Log.d(LOGGING_TAG, "HTML:")
//                Utils.logLongD(LOGGING_TAG, resultParsed)
                val doc = Jsoup.parse(resultParsed)
                processUI(doc)
            }
        }
    }

    fun processUI(doc: Document) {
        Log.d(LOGGING_TAG, "Processing UI...")


        val navBar = (activity as MainActivity).bottomNavigationView
        val fab = (activity as MainActivity).speedDial
        val defaultAccentColor = ResourcesCompat.getColor(resources, R.color.colorAccent, requireContext().theme)
        val lightColor = ResourcesCompat.getColor(resources, R.color.colorPrimary, requireContext().theme)
        val darkColor = ResourcesCompat.getColor(resources, R.color.colorPrimaryDark, requireContext().theme)

        if (ItchWebsiteUtils.shouldRemoveAppNavbar(webView, doc)) {
            navBar?.post {
                navBar.visibility = View.GONE
            }
            fab?.post {
                val fabParams = fab.layoutParams as ViewGroup.MarginLayoutParams
                val marginDP = (50 * requireContext().resources.displayMetrics.density).toInt()
                fabParams.bottomMargin = marginDP
            }
        } else {
            navBar?.post {
                navBar.visibility = View.VISIBLE
            }
            fab?.post {
                val fabParams = fab.layoutParams as ViewGroup.MarginLayoutParams
                fabParams.bottomMargin = 0
            }
        }
        launch(Dispatchers.Default) {
            //Colors adapt to game theme

            val gameThemeColor = ItchWebsiteParser.getBackgroundUIColor(doc)

            val accentColor = gameThemeColor ?: defaultAccentColor
            val bgColor = gameThemeColor ?: lightColor
            val fgColor = if (gameThemeColor == null) darkColor else lightColor

            fab?.post {
                fab.mainFabClosedBackgroundColor = accentColor
                fab.mainFabOpenedBackgroundColor = accentColor
                for (actionItem in fab.actionItems) {
                    val newActionItem = SpeedDialActionItem.Builder(actionItem)
                        .setFabBackgroundColor(bgColor)
                        .setLabelBackgroundColor(bgColor)
                        .setFabImageTintColor(fgColor)
                        .setLabelColor(fgColor)
                        .create()
                    fab.replaceActionItem(actionItem, newActionItem)
                }
            }
            progressBar.post {
                progressBar.setBackgroundColor(bgColor)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        processUI()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(LOGGING_TAG, "Saving WebView")

        val webViewState = Bundle()
        webView.saveState(webViewState)
        outState.putBundle(WEB_VIEW_STATE_KEY, webViewState)

        super.onSaveInstanceState(outState)
    }




    @Keep //prevent this class from being removed by compiler optimizations
    private class ItchJavaScriptInterface(val fragment: BrowseFragment) {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadId: String) {
            Log.d(LOGGING_TAG, "Selected upload ID: $uploadId")
            fragment.launch(Dispatchers.IO) {
                fragment.browseHandler?.setClickedUploadId(uploadId.toInt())
            }
        }

        @JavascriptInterface
        fun onHtmlLoaded(html: String, url: String) {
            Log.d(LOGGING_TAG, url)
            if(fragment.activity !is MainActivity)
                return

//            Log.d(LOGGING_TAG, "HTML:")
//            Utils.logLongD(LOGGING_TAG, html)

            fragment.launch(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                fragment.browseHandler?.onPageVisited(doc, url)
                fragment.processUI(doc)
            }
        }
    }




    inner class MitchWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (ItchWebsiteUtils.isItchWebPage(uri))
                return false
            else {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return true
            }
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.context)
            val blockTrackers = sharedPreferences.getBoolean("preference_block_trackers", true)

            if (blockTrackers) {
                val blockedURLs = arrayOf(
                    "www.google-analytics.com",
                    "adservice.google.com",
                    "pagead2.googlesyndication.com",
                    "googleads.g.doubleclick.net"
                )
                if (blockedURLs.contains(request.url.host))
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream("tracker_blocked".toByteArray())
                    )
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            view.evaluateJavascript("""
                    document.addEventListener("DOMContentLoaded", (event) => {
                        mitchCustomJS.onHtmlLoaded("<html>" + document.getElementsByTagName("html")[0].innerHTML + "</html>",
                                                   window.location.href);
                    });
                """, null
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript("""
                    let mitchCustomJS_elements = document.getElementsByClassName("download_btn");
                    for (var mitchCustomJS_element of mitchCustomJS_elements) {
                        let mitchCustomJS_uploadId = mitchCustomJS_element.getAttribute("data-upload_id");
                        mitchCustomJS_element.addEventListener("click", (event) => {
                            mitchCustomJS.onDownloadLinkClick(mitchCustomJS_uploadId);
                        });
                    }
                """, null
            )
        }
    }

    inner class MitchWebChromeClient : WebChromeClient() {
        private var customView: View? = null
        private var originalUiVisibility: Int = View.SYSTEM_UI_FLAG_VISIBLE
        var customViewCallback: CustomViewCallback? = null
            private set
        private var originalNavBarVisibility: Int = View.VISIBLE

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            Log.d(LOGGING_TAG, "onShowCustomView")
            if (customView != null) {
                Log.d(LOGGING_TAG, "return")
                return
            }

            val mainActivity = activity as? MainActivity
            originalNavBarVisibility = mainActivity?.bottomNavigationView?.visibility ?: View.VISIBLE
            mainActivity?.bottomNavigationView?.visibility = View.GONE
            webView.visibility = View.GONE

            if(mainActivity?.fragmentContainer != null) {
                mainActivity.fragmentContainer.addView(view)

                originalUiVisibility = mainActivity.fragmentContainer.systemUiVisibility
                mainActivity.fragmentContainer.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            view.keepScreenOn = true

            customView = view
            customViewCallback = callback
        }

        override fun onHideCustomView() {
            Log.d(LOGGING_TAG, "onHideCustomView")
            if (customView == null) {
                Log.d(LOGGING_TAG, "return")
                return
            }

            val mainActivity = activity as? MainActivity
            mainActivity?.bottomNavigationView?.visibility = originalNavBarVisibility
            webView.visibility = View.VISIBLE

            mainActivity?.fragmentContainer?.removeView(customView)
            mainActivity?.fragmentContainer?.systemUiVisibility = originalUiVisibility

            customView = null
            customViewCallback = null
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (newProgress < 100 && progressBar.visibility == ProgressBar.GONE)
                progressBar.visibility = ProgressBar.VISIBLE

            //TODO: better animation?
            progressBar.progress = newProgress

            if (newProgress == 100)
                progressBar.visibility = ProgressBar.GONE
        }
    }
}