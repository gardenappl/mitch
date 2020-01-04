package ua.gardenapple.itchupdater.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.jsoup.Jsoup
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.web.DownloadRequester
import java.io.ByteArrayInputStream
import android.webkit.ValueCallback



class BrowseFragment : Fragment() {
    private lateinit var webView: MitchWebView

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
                //TODO: make this optional, on by default for F-Droid
                val blockedURLs = arrayOf("www.google-analytics.com", "adservice.google.com", "pagead2.googlesyndication.com", "googleads.g.doubleclick.net")
                if (blockedURLs.contains(request.url.host))
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("tracker_blocked".toByteArray()))
                else {
                    return null
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                webView.evaluateJavascript("""
                    document.addEventListener("DOMContentLoaded", (event) => {
                        mitchCustomJS.processHTML("<html>" + document.getElementsByTagName("html")[0].innerHTML + "</html>");
                    });
                """, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript("""
                    var elements = document.getElementsByClassName("download_btn");
                    for(var element of elements) {
                        element.addEventListener("click", function() {
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

        if(savedInstanceState != null) {
            Log.d(LOGGING_TAG, "Restoring WebView")
            webView.restoreState(savedInstanceState)
        } else
            webView.loadUrl("https://itch.io/games/platform-android")
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

    fun getWebView(): MitchWebView {
        return webView
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(LOGGING_TAG, "Saving WebView")
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private class ItchJavaScriptInterface(val fragment: BrowseFragment) {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadID: String) {
            Log.d(LOGGING_TAG, uploadID)
        }

        @JavascriptInterface
        fun processHTML(html: String) {
            if(fragment.activity !is MainActivity)
                return

            Log.d(LOGGING_TAG, "HTML: " + html)
            fragment.adjustUIBasedOnWebsite(html)
        }
    }

    protected fun adjustUIBasedOnWebsite(html: String) {
        if(activity == null || activity !is MainActivity)
            return

        val doc = Jsoup.parse(html)

        val mainActivity = activity as MainActivity
        val navBar = mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        navBar.post {
            if(ItchWebsiteUtils.shouldRemoveAppNavbar(getWebView(), doc))
                navBar.visibility = View.GONE
            else
                navBar.visibility = View.VISIBLE
        }
    }

    protected fun adjustUIBasedOnWebsite() {
        webView.evaluateJavascript(
        "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();"
        ) { result -> adjustUIBasedOnWebsite(result) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)


        adjustUIBasedOnWebsite()
    }
}