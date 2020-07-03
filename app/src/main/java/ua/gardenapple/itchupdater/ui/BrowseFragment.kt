package ua.gardenapple.itchupdater.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.browse_fragment.*
import kotlinx.android.synthetic.main.dialog_search.view.*
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


class BrowseFragment : Fragment(), CoroutineScope by MainScope() {

    companion object {
        const val LOGGING_TAG = "BrowseFragment"
        const val WEB_VIEW_STATE_KEY: String = "WebView"
    }

    private lateinit var chromeClient: MitchWebChromeClient
    lateinit var webView: MitchWebView
        private set

    var browseHandler: ItchBrowseHandler? = null
    private var currentDoc: Document? = null


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
                    downloadId: Long ->
                    run {
                        Toast.makeText(context, R.string.toast_download_started, Toast.LENGTH_LONG)
                            .show()
                        browseHandler!!.onDownloadStarted(downloadId)
                    }
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

                    val input = viewInflated.input

                    //Show keyboard automatically
                    input.post {
                        input.requestFocus()
                        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                            as InputMethodManager
                        inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                    }


                    val alertDialog = AlertDialog.Builder(requireContext())
                        .setTitle(R.string.browser_search)
                        .setView(viewInflated)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            webView.loadUrl(ItchWebsiteUtils.getSearchUrl(input.text.toString()))
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()

                    input.setOnEditorActionListener { textView, actionId, keyEvent ->
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            alertDialog.dismiss()
                            webView.loadUrl(ItchWebsiteUtils.getSearchUrl(input.text.toString()))
                            return@setOnEditorActionListener true
                        }
                        false
                    }

                    return@setOnActionSelectedListener true
                }
                //TODO: open in browser
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
            webView.loadUrl(ItchWebsiteUtils.getMainBrowsePage())
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

    /*private fun updateUI() {
        //Hack for retrieving HTML from WebView
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
                currentDoc = Jsoup.parse(resultParsed)
                updateUI(currentDoc)
            }
        }
    }*/

    /**
     * Adapts the app's UI to the theme of the current web page.
     */
    fun updateUI() {
        updateUI(currentDoc)
    }

    /**
     * Adapts the app's UI to the theme of a web page.
     * @param doc the parsed DOM of the page the user is currently on. Null if the UI shouldn't adapt to any web page at all
     */
    private fun updateUI(doc: Document?) {
        Log.d(LOGGING_TAG, "Processing UI...")
        val mainActivity = activity as MainActivity

        if (mainActivity.activeFragment != mainActivity.browseFragment)
            return

        val navBar = (activity as? MainActivity)?.bottomNavigationView
        val fab = (activity as? MainActivity)?.speedDial
        val supportAppBar = (activity as? MainActivity)?.supportActionBar
        val appBar = (activity as? MainActivity)?.toolbar


        if (doc != null && ItchWebsiteUtils.isStylizedGamePage(doc)) {
            fab?.post {
                val fabParams = fab.layoutParams as ViewGroup.MarginLayoutParams
                val marginDP = (50 * requireContext().resources.displayMetrics.density).toInt()
                fabParams.bottomMargin = marginDP
            }
            navBar?.post {
                navBar.visibility = View.GONE
            }
            appBar?.post {
                val appBarTitle = "<b>${Html.escapeHtml(ItchWebsiteParser.getGameName(doc))}</b>"
                if (Build.VERSION.SDK_INT >= 24)
                    supportAppBar?.title = Html.fromHtml(appBarTitle, 0)
                else
                    supportAppBar?.title = Html.fromHtml(appBarTitle)
                supportAppBar?.show()
                setupAppBarMenu(doc, appBar)
            }
        } else {
            fab?.post {
                val fabParams = fab.layoutParams as ViewGroup.MarginLayoutParams
                fabParams.bottomMargin = 0
            }
            navBar?.post {
                navBar.visibility = View.VISIBLE
            }
            appBar?.post {
                supportAppBar?.hide()
            }
        }


        //Colors adapt to game theme

        val defaultAccentColor = ResourcesCompat.getColor(resources, R.color.colorAccent, requireContext().theme)
        val defaultBgColor = ResourcesCompat.getColor(resources, R.color.colorPrimary, requireContext().theme)
        val defaultFgColor = ResourcesCompat.getColor(resources, R.color.colorPrimaryDark, requireContext().theme)

        //TODO: change color of system UI
        launch(Dispatchers.Default) {
            val gameThemeColor = doc?.run { ItchWebsiteParser.getBackgroundUIColor(doc) }

            val accentColor = gameThemeColor ?: defaultAccentColor
            val bgColor = gameThemeColor ?: defaultBgColor
            val fgColor = if (gameThemeColor == null) defaultFgColor else defaultBgColor

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
            progressBar?.post {
                progressBar.setBackgroundColor(bgColor)
            }
            appBar?.post {
                appBar.setBackgroundColor(bgColor)
                appBar.setTitleTextColor(fgColor)
                appBar.overflowIcon?.setTint(fgColor)
            }
        }
    }

    private fun setupAppBarMenu(doc: Document, appBar: Toolbar) {
        appBar.menu.clear()

        //5px of padding on left and right, min width for item is 80px
        val navbarItemFitCount = (webView.contentWidth - 10) / 80
        val navbarItems = doc.getElementById("user_tools").children()
        val navbarItemsCount = navbarItems.size

        while (navbarItemFitCount < navbarItemsCount) {
            val lastItem = navbarItems.last()

            if (lastItem.getElementsByClass("related_games_btn").isNotEmpty()) {
                appBar.menu.add(Menu.NONE, 3, 3, R.string.menu_game_related).setOnMenuItemClickListener {
                    val gameId = ItchWebsiteUtils.getGameId(doc)
                    webView.loadUrl("https://itch.io/games-like/$gameId")
                    true
                }
            } else if (lastItem.getElementsByClass("rate_game_btn").isNotEmpty()) {
                appBar.menu.add(Menu.NONE, 2, 2, R.string.menu_game_rate).setOnMenuItemClickListener {
                    webView.loadUrl(webView.url + "/rate?source=game")
                    true
                }
            } else if (lastItem.hasClass("devlog_link")) {
                appBar.menu.add(Menu.NONE, 1, 1, R.string.menu_game_devlog).setOnMenuItemClickListener {
                    webView.loadUrl(webView.url + "/devlog")
                    true
                }
            } else if (lastItem.getElementsByClass("add_to_collection_btn").isNotEmpty()) {
                appBar.menu.add(Menu.NONE, 0, 0, R.string.menu_game_collection).setOnMenuItemClickListener {
                    webView.loadUrl(webView.url + "/add-to-collection?source=game")
                    true
                }
            } else
                break

            navbarItems.removeAt(navbarItems.size - 1)
        }

        appBar.menu.add(Menu.NONE, 4, 4, R.string.nav_installed).setOnMenuItemClickListener {
            updateUI(null)
            (activity as MainActivity).switchToFragment(R.id.navigation_library, true)
            true
        }
        appBar.menu.add(Menu.NONE, 5, 5, R.string.nav_settings).setOnMenuItemClickListener {
            updateUI(null)
            (activity as MainActivity).switchToFragment(R.id.navigation_settings, true)
            true
        }
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateUI()
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
                fragment.currentDoc = doc
                fragment.updateUI()
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
            //TODO: better animation for progress bar appearing/disappearing?
            if (newProgress < 100 && progressBar.visibility == ProgressBar.GONE)
                progressBar.visibility = ProgressBar.VISIBLE

            progressBar.progress = newProgress

            if (newProgress == 100)
                progressBar.visibility = ProgressBar.GONE
        }
    }
}