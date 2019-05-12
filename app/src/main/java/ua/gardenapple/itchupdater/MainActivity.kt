package ua.gardenapple.itchupdater

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.webkit.*
import ua.gardenapple.itchupdater.client.web.DownloadRequester
import java.io.ByteArrayInputStream


class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.main_web_view)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                Log.d(LOGGING_TAG, uri.toString())
                if (uri.host == "itch.io" ||
                        uri.host.endsWith(".itch.io") ||
                        uri.host.endsWith(".itch.zone") ||
                        uri.host.endsWith(".hwcdn.net"))
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
                            mitch.onDownloadLinkClick(element.getAttribute("data-upload_id"));
                        });
                    }
                """, null)
            }
        }
        webView.addJavascriptInterface(ItchJavaScriptInterface(), "mitch")

        webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            DownloadRequester.requestDownload(this, url, contentDisposition, mimetype)
        }

        webView.loadUrl("https://itch.io/games/platform-android")
    }

    override fun onBackPressed() {
        if(webView.canGoBack())
            webView.goBack()
        else
            super.onBackPressed()
    }

    private class ItchJavaScriptInterface {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadID: String) {
            Log.d(LOGGING_TAG, uploadID)
        }
    }
}
