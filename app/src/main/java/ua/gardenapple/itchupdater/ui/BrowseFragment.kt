package ua.gardenapple.itchupdater.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.ProgressBar
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.client.ItchBrowseHandler
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.databinding.BrowseFragmentBinding
import java.io.ByteArrayInputStream
import java.io.File


class BrowseFragment : Fragment(), CoroutineScope by MainScope() {

    companion object {
        private const val LOGGING_TAG = "BrowseFragment"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"
    }
    
    private var _binding: BrowseFragmentBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var chromeClient: MitchWebChromeClient
    lateinit var webView: MitchWebView
        private set

    var browseHandler: ItchBrowseHandler? = null
    private var currentDoc: Document? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)
        browseHandler = ItchBrowseHandler(context, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = BrowseFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            browseHandler?.onDownloadStarted(url, contentDisposition, mimeType)
        }


        //Set up FAB buttons
        //(colors don't matter too much as they will be set by updateUI() anyway)
        val speedDialView = (activity as MainActivity).binding.speedDial
        speedDialView.clearActionItems()
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_reload, R.drawable.ic_baseline_refresh_24)
            .setLabel(R.string.browser_reload)
            .create()
        )
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_search, R.drawable.ic_baseline_search_24)
            .setLabel(R.string.browser_search)
            .create()
        )
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_open_in_browser, R.drawable.ic_baseline_open_in_browser_24)
            .setLabel(R.string.browser_open_in_browser)
            .create()
        )
        speedDialView.addActionItem(SpeedDialActionItem.Builder(R.id.browser_share, R.drawable.ic_baseline_share_24)
            .setLabel(R.string.browser_share)
            .create()
        )
        
        speedDialView.setOnActionSelectedListener { actionItem ->
            speedDialView.close()

            when (actionItem.id) {
                R.id.browser_reload -> {
                    webView.reload()
                    return@setOnActionSelectedListener true
                }
                R.id.browser_share -> {
                    ShareCompat.IntentBuilder.from(requireActivity())
                        .setType("text/plain")
                        .setChooserTitle(R.string.browser_share)
                        .setText(webView.url)
                        .startChooser()
                    return@setOnActionSelectedListener true
                }
                R.id.browser_open_in_browser -> {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(webView.url)

                    val title = resources.getString(R.string.browser_open_in_browser)
                    val chooser = Intent.createChooser(intent, title)

                    startActivity(chooser)
                    return@setOnActionSelectedListener true
                }
                R.id.browser_search -> {
                    //Search dialog

                    val viewInflated: View = LayoutInflater.from(context)
                        .inflate(R.layout.dialog_search, getView() as ViewGroup?, false)

                    val input = viewInflated.findViewById<TextInputEditText>(R.id.input)

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

                    input.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            alertDialog.dismiss()
                            webView.loadUrl(ItchWebsiteUtils.getSearchUrl(input.text.toString()))
                            return@setOnEditorActionListener true
                        }
                        false
                    }

                    return@setOnActionSelectedListener true
                }
                else -> {
                    return@setOnActionSelectedListener false
                }
            }
        }


        //Load page, this will also update the UI
        if(savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY) != null) {
            webView.restoreState(savedInstanceState.getBundle(WEB_VIEW_STATE_KEY))
        } else {
            webView.loadUrl(ItchWebsiteUtils.getMainBrowsePage(requireContext()))
        }
    }

    /**
     * @return true if the user can't go back in the web history
     */
    fun onBackPressed(): Boolean {
        val customViewCallback = chromeClient.customViewCallback
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden()
            return false
        }

        if(webView.canGoBack()) {
            webView.goBack()
            return false
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webViewState = Bundle()
        webView.saveState(webViewState)
        outState.putBundle(WEB_VIEW_STATE_KEY, webViewState)

        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()

        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()

        requireContext().stopService(Intent(context, WebViewForegroundService::class.java))

        cancel()
    }

    override fun onDetach() {
        super.onDetach()

        browseHandler = null
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    val isWebFullscreen: Boolean
        get() = chromeClient.customViewCallback != null

    /**
     * Adapts the app's UI to the theme of the current web page.
     */
    fun updateUI() {
        updateUI(currentDoc)
    }

    fun restoreDefaultUI() {
        updateUI(null)
    }

    //TODO: hide stuff on scroll?
    /**
     * Adapts the app's UI to the theme of a web page. Should only affect the UI while the browse
     * fragment is selected.
     * Must run on the UI thread!
     * @param doc the parsed DOM of the page the user is currently on. Null if the UI shouldn't adapt to any web page at all
     */
    private fun updateUI(doc: Document?) {
        if (!this::chromeClient.isInitialized || isWebFullscreen)
            return

        val mainActivity = activity as? MainActivity ?: return

        if (!isVisible && doc != null)
            return

        //Log.d(LOGGING_TAG, "Processing UI...")

        val navBar = mainActivity.binding.bottomNavigationView
        val fab = mainActivity.binding.speedDial
        val supportAppBar = mainActivity.supportActionBar!!
        val appBar = mainActivity.binding.toolbar

        fab.show()

        hideUnwantedElements()

        if (doc != null && ItchWebsiteUtils.isStylizedPage(doc)) {
            if (ItchWebsiteUtils.isGamePage(doc)) {
                //Hide app's navbar after hiding web navbar
                val navBarHideCallback: (String) -> Unit = {
                    if (isVisible)
                        navBar.visibility = View.GONE
                }
                if (ItchWebsiteUtils.siteHasNavbar(webView, doc)) {
                    setSiteNavbarVisibility(false, navBarHideCallback)
                } else {
                    setSiteNavbarVisibility(true, navBarHideCallback)
                }

                val appBarTitle =
                    "<b>${Html.escapeHtml(ItchWebsiteParser.getGameName(doc))}</b>"

                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 24)
                    supportAppBar.title = Html.fromHtml(appBarTitle, 0)
                else
                    supportAppBar.title = Html.fromHtml(appBarTitle)

                appBar.menu.clear()
                addAppBarActions(appBar, doc)
                addDefaultAppBarActions(appBar)
                supportAppBar.show()
            } else if (ItchWebsiteUtils.isUserPage(doc)) {
                val appBarTitle =
                    "<b>${Html.escapeHtml(ItchWebsiteParser.getUserName(doc))}</b>"

                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 24)
                    supportAppBar.title = Html.fromHtml(appBarTitle, 0)
                else
                    supportAppBar.title = Html.fromHtml(appBarTitle)

                appBar.menu.clear()
                addDefaultAppBarActions(appBar)
                supportAppBar.show()

                navBar.visibility = View.GONE
            }
        } else {
            navBar.visibility = View.VISIBLE
            supportAppBar.hide()
        }

        //Colors adapt to game theme

        val defaultAccentColor = Utils.getColor(resources, R.color.colorAccent, requireContext().theme)
        val defaultWhiteColor = Utils.getColor(resources, R.color.colorPrimary, requireContext().theme)
        val defaultBlackColor = Utils.getColor(resources, R.color.colorPrimaryDark, requireContext().theme)

        val defaultBgColor = Utils.getColor(resources, R.color.colorBackground, requireContext().theme)
        val defaultFgColor = Utils.getColor(resources, R.color.colorForeground, requireContext().theme)

        val gameThemeBgColor = doc?.run { ItchWebsiteUtils.getBackgroundUIColor(doc) }
        val gameThemeButtonColor = doc?.run { ItchWebsiteUtils.getAccentUIColor(doc) }
        val gameThemeButtonFgColor = doc?.run { ItchWebsiteUtils.getAccentFgUIColor(doc) }

        val accentColor = gameThemeButtonColor ?: defaultAccentColor
        val accentFgColor = gameThemeButtonFgColor ?: defaultWhiteColor

        val bgColor = gameThemeBgColor ?: defaultBgColor
        val fgColor = if (gameThemeBgColor == null) defaultFgColor else defaultWhiteColor

        fab.mainFabClosedBackgroundColor = accentColor
        fab.mainFabOpenedBackgroundColor = accentColor
        fab.mainFabClosedIconColor = accentFgColor
        fab.mainFabOpenedIconColor = accentFgColor
        for (actionItem in fab.actionItems) {
            val newActionItem = SpeedDialActionItem.Builder(actionItem)
                .setFabBackgroundColor(bgColor)
                .setFabImageTintColor(fgColor)
                .setLabelBackgroundColor(bgColor)
                .setLabelColor(fgColor)
                .create()
            fab.replaceActionItem(actionItem, newActionItem)
        }
        binding.progressBar.progressDrawable.setTint(accentColor)
        appBar.setBackgroundColor(bgColor)
        appBar.setTitleTextColor(fgColor)
        appBar.overflowIcon?.setTint(fgColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mainActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            mainActivity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            mainActivity.window.statusBarColor = bgColor
            if (fgColor == defaultBlackColor)
                mainActivity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else
                mainActivity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    /**
     * Add app bar actions from itch.io toolbar, which appears on game pages and devlog pages.
     * Does not clear the current list.
     *
     * Should run on the UI thread!
     *
     * @param doc the parsed HTML document of a game store page or devlog page
     * @param appBar the app UI's top Toolbar
     */
    private fun addAppBarActions(appBar: Toolbar, doc: Document) {
        appBar.menu.clear()

        val navbarItems = doc.getElementById("user_tools").children()

        while (navbarItems.isNotEmpty()) {
            val item = navbarItems.last()
            val url = item.child(0).attr("href")

            if (item.getElementsByClass("related_games_btn").isNotEmpty()) {
                appBar.menu.add(Menu.NONE, 5, 5, R.string.menu_game_related)
                    .setOnMenuItemClickListener {
                        webView.loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("rate_game_btn").isNotEmpty()) {
                appBar.menu.add(Menu.NONE, 4, 4, R.string.menu_game_rate)
                    .setOnMenuItemClickListener {
                        webView.loadUrl(url)
                        true
                    }
                    .setIcon(R.drawable.ic_baseline_rate_review_24)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)

            } else if (item.hasClass("devlog_link")) {
                appBar.menu.add(Menu.NONE, 3, 3, R.string.menu_game_devlog)
                    .setOnMenuItemClickListener {
                        webView.loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("add_to_collection_btn").isNotEmpty()) {
                appBar.menu.add(Menu.NONE, 2, 2, R.string.menu_game_collection)
                    .setOnMenuItemClickListener {
                        webView.loadUrl(url)
                        true
                    }

            } else if (item.hasClass("jam_entry")) {
                //TODO: handle multiple jam entries nicely
                val menuItemName = item.child(0).text()

                appBar.menu.add(Menu.NONE, 1, 1, menuItemName)
                    .setOnMenuItemClickListener {
                        webView.loadUrl(url)
                        true
                    }
                    .setIcon(R.drawable.ic_baseline_emoji_events_24)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)

            } else if (item.getElementsByClass("view_more").isNotEmpty()) {
                //Cannot rely on ItchWebsiteParser, because its method requires the current URL,
                //and while loading another page, webView.url changes prematurely
                //(leading to crashes...)
                val authorName = item.getElementsByClass("mobile_label")[0].text()

                val menuItemName =
                    if (item.getElementsByClass("full_label")[0].text().contains(authorName))
                        resources.getString(R.string.menu_game_author, authorName)
                    else
                        resources.getString(R.string.menu_game_author_generic)

                appBar.menu.add(Menu.NONE, 0, 0, menuItemName).setOnMenuItemClickListener {
                    webView.loadUrl(url)
                    true
                }
            }

            navbarItems.removeAt(navbarItems.size - 1)
        }
    }

    /**
     * Adds basic app bar actions for navigating between fragments.
     * Should run on UI thread.
     *
     * @param appBar the application's top toolbar
     */
    private fun addDefaultAppBarActions(appBar: Toolbar) {
        appBar.menu.add(Menu.NONE, 10, 10, R.string.nav_installed).setOnMenuItemClickListener {
            (activity as MainActivity).setActiveFragment(MainActivity.LIBRARY_FRAGMENT_TAG, true)
            true
        }
        appBar.menu.add(Menu.NONE, 11, 11, R.string.nav_settings).setOnMenuItemClickListener {
            (activity as MainActivity).setActiveFragment(MainActivity.SETTINGS_FRAGMENT_TAG, true)
            true
        }
    }

    private fun setSiteNavbarVisibility(visible: Boolean, callback: (String) -> (Unit)) {
        val cssVisibility = if (visible) "visible" else "hidden"
        webView.post {
            webView.evaluateJavascript("""
                    {
                        let navbar = document.getElementById("user_tools")
                        if (navbar)
                            navbar.style.visibility = "$cssVisibility"
                    }
                """, callback
            )
        }
    }

    private fun hideUnwantedElements() {
        webView.post {
            webView.evaluateJavascript("""
                    {
                        let elements = document.getElementsByClassName("youtube_mobile_banner_widget")
                        for (var element of elements)
                            element.style.visibility = "hidden"
                    }
                """, null
            )
        }
    }



    @Keep //prevent this class from being removed by compiler optimizations
    private class ItchJavaScriptInterface(val fragment: BrowseFragment) {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadId: String) {
            fragment.launch(Dispatchers.IO) {
                fragment.browseHandler?.setClickedUploadId(uploadId.toInt())
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
                fragment.currentDoc = doc
                fragment.activity?.runOnUiThread {
                    fragment.updateUI()
                }
            }
        }

        @JavascriptInterface
        fun onResize() {
            fragment.activity?.runOnUiThread {
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
                arrayOf(
                    "google-analytics.com",
                    "adservice.google.com",
                    "googlesyndication.com",
                    "doubleclick.net"
                ).forEach { trackerHostUrl ->
                    if (request.url.host?.contains(trackerHostUrl) == true)
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream("tracker_blocked".toByteArray())
                        )
                }
            }
//            Log.d(LOGGING_TAG, request.url.host)
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            view.evaluateJavascript("""
                    document.addEventListener("DOMContentLoaded", (event) => {
                        mitchCustomJS.onHtmlLoaded("<html>" + document.getElementsByTagName("html")[0].innerHTML + "</html>",
                                                   window.location.href);
                    });
                    window.addEventListener("resize", (event) => {
                        mitchCustomJS.onResize();
                    });
                """, null
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript("""
                    {
                        //local scope variables only
                        let downloadButtons = document.getElementsByClassName("download_btn");
                        for (var downloadButton of downloadButtons) {
                            let uploadId = downloadButton.getAttribute("data-upload_id");
                            downloadButton.addEventListener("click", (event) => {
                                mitchCustomJS.onDownloadLinkClick(uploadId);
                            });
                        }
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

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                return
            }

            val foregroundServiceIntent = Intent(context, WebViewForegroundService::class.java)
            foregroundServiceIntent.putExtra(WebViewForegroundService.EXTRA_ORIGINAL_INTENT,
                requireActivity().intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(foregroundServiceIntent)
            } else {
                requireContext().startService(foregroundServiceIntent)
            }

            webView.visibility = View.GONE
            
            (activity as? MainActivity)?.apply {
                binding.bottomNavigationView.visibility = View.GONE
                binding.speedDial.visibility = View.GONE
                binding.toolbar.visibility = View.GONE


                binding.fragmentContainer.addView(view)

                originalUiVisibility = binding.root.systemUiVisibility
                binding.root.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            

            view.keepScreenOn = true

            customView = view
            customViewCallback = callback
        }

        override fun onHideCustomView() {
            if (customView == null) {
                return
            }

            requireContext().stopService(Intent(context, WebViewForegroundService::class.java))

            webView.visibility = View.VISIBLE
            (activity as? MainActivity)?.apply {
                binding.fragmentContainer.removeView(customView)
                binding.root.systemUiVisibility = originalUiVisibility
            }

            customView = null
            customViewCallback = null

            updateUI()
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (_binding == null)
                return

            val progressBar = binding.progressBar
            
            if (newProgress < 100 && progressBar.visibility == ProgressBar.GONE)
                progressBar.visibility = ProgressBar.VISIBLE

            progressBar.progress = newProgress

            if (newProgress == 100)
                progressBar.visibility = ProgressBar.GONE
        }
    }
}