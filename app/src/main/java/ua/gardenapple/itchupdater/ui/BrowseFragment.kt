package ua.gardenapple.itchupdater.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.browse_fragment.view.*
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.web.DownloadRequester
import java.io.ByteArrayInputStream

class BrowseFragment : Fragment() {
    private lateinit var webView: WebView

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
                if (uri.host == "itch.io" ||
                    uri.host!!.endsWith(".itch.io") ||
                    uri.host!!.endsWith(".itch.zone") ||
                    uri.host!!.endsWith(".hwcdn.net"))
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

            override fun onPageFinished(view: WebView, url: String) {
                //TODO: maybe not neccessary to do on every page load?
                CookieManager.getInstance().flush()


                //TODO: filter only store pages
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
        webView.addJavascriptInterface(ItchJavaScriptInterface(), "mitchCustomJS")

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            activity!!.let { DownloadRequester.requestDownload(it, url, contentDisposition, mimeType) }
        }

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

    fun getWebView(): WebView {
        return webView
    }

    private class ItchJavaScriptInterface() {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadID: String) {
            Log.d(LOGGING_TAG, uploadID)
        }
    }
}