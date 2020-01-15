package ua.gardenapple.itchupdater.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.jsoup.Jsoup
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.installer.DownloadRequester
import java.io.ByteArrayInputStream
import androidx.annotation.Keep
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.client.ItchBrowseHandler
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.database.AppDatabase
import kotlin.coroutines.CoroutineContext


class BrowseFragment : Fragment(), CoroutineScope by MainScope() {

    companion object {
        const val LOGGING_TAG = "BrowseFragment"
        const val WEB_VIEW_STATE_KEY: String = "WebView"
    }

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

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
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

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.context)
                val blockTrackers = sharedPreferences.getBoolean("preference_block_trackers", true)

                if(blockTrackers) {
                    val blockedURLs = arrayOf(
                        "www.google-analytics.com",
                        "adservice.google.com",
                        "pagead2.googlesyndication.com",
                        "googleads.g.doubleclick.net"
                    )
                    if (blockedURLs.contains(request.url.host))
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("tracker_blocked".toByteArray()))
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                webView.evaluateJavascript("""
                    document.addEventListener("DOMContentLoaded", (event) => {
                        mitchCustomJS.onHtmlLoaded("<html>" + document.getElementsByTagName("html")[0].innerHTML + "</html>",
                                                   window.location.href);
                    });
                """, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript("""
                    var elements = document.getElementsByClassName("download_btn");
                    for(var element of elements) {
                        element.addEventListener("click", (event) => {
                            mitchCustomJS.onDownloadLinkClick(element.getAttribute("data-upload_id"));
                        });
                    }
                """, null)
            }
        }
        webView.addJavascriptInterface(ItchJavaScriptInterface(this), "mitchCustomJS")

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            activity!!.let { DownloadRequester.requestDownload(it, url, contentDisposition, mimeType) }
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

    @Keep //prevent this class from being removed by compiler optimizations
    private class ItchJavaScriptInterface(val fragment: BrowseFragment) {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadID: String) {
            Log.d(LOGGING_TAG, uploadID)
        }

        @JavascriptInterface
        fun onHtmlLoaded(html: String, url: String) {
            if(fragment.activity !is MainActivity)
                return

            fragment.launch(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                fragment.browseHandler?.processItchData(doc, url)
                fragment.processUI(doc)
            }
        }
    }

    fun processUI(doc: Document) {
        val mainActivity = activity as MainActivity
        val navBar = mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        navBar.post {
            if (ItchWebsiteUtils.shouldRemoveAppNavbar(webView, doc))
                navBar.visibility = View.GONE
            else
                navBar.visibility = View.VISIBLE
        }
    }
    /**
     * Responsible for updating the UI as well as the local game database after a page has loaded.
     */
    protected fun processUI() {
        webView.evaluateJavascript(
            """return '<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>';"""
        ) { result ->
            launch(Dispatchers.Default) {
                val doc = Jsoup.parse(result)
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
}