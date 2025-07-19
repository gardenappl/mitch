package garden.appl.mitch.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.R
import java.io.ByteArrayInputStream

open class MitchWebViewClient : WebViewClient() {
    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return shouldOverrideUrlLoading(view, url.toUri())
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return shouldOverrideUrlLoading(view, request.url)
    }

    protected open fun shouldOverrideUrlLoading(view: WebView, uri: Uri): Boolean {
        if (ItchWebsiteUtils.isItchWebPageOrCDN(uri)) {
            return false
        } else {
            val context = view.context
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(
                    context,
                    context.resources.getString(R.string.popup_handler_app_not_found, uri),
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
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        val blockTrackers = sharedPreferences.getBoolean("preference_block_trackers", true)

        if (blockTrackers) {
            arrayOf(
                "google-analytics.com",
                "adservice.google.com",
                "googlesyndication.com",
                "doubleclick.net",
                "crashlytics.com"
            ).forEach { trackerHostUrl ->
                if (request.url.host == trackerHostUrl ||
                    request.url.host?.endsWith('.' + trackerHostUrl) == true) {

                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream("tracker_blocked".toByteArray())
                    )
                }
            }
        }
        return null
    }
}