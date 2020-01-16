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
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.ItchBrowseHandler
import ua.gardenapple.itchupdater.installer.DownloadRequester
import java.io.ByteArrayInputStream
import java.io.StringReader


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
        browseHandler = ItchBrowseHandler(context)
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

        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = MitchWebViewClient()
        webView.webChromeClient = chromeClient

        webView.addJavascriptInterface(ItchJavaScriptInterface(this), "mitchCustomJS")

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            context!!.let { DownloadRequester.requestDownload(it, activity, url, contentDisposition, mimeType) }
        }

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


    fun processUI(doc: Document) {
        Log.d(LOGGING_TAG, "Processing UI...")
        val navBar = (activity as? MainActivity)?.bottomNavigationView
        navBar?.post {
            if (ItchWebsiteUtils.shouldRemoveAppNavbar(webView, doc))
                navBar.visibility = View.GONE
            else
                navBar.visibility = View.VISIBLE
        }
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
                fragment.browseHandler?.onGameDownloadStarted(uploadId.toInt())
            }
        }

        @JavascriptInterface
        fun onHtmlLoaded(html: String, url: String) {
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
            Log.d(LOGGING_TAG, uri.toString())
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
                    var elements = document.getElementsByClassName("download_btn");
                    for(var element of elements) {
                        element.addEventListener("click", (event) => {
                            mitchCustomJS.onDownloadLinkClick(element.getAttribute("data-upload_id"));
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
    }
}