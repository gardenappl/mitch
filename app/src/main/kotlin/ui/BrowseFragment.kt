package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.SpannedString
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import garden.appl.mitch.*
import garden.appl.mitch.client.ItchBrowseHandler
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.client.SpecialBundleHandler
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.databinding.BrowseFragmentBinding
import garden.appl.mitch.databinding.DialogWebPromptBinding
import java.io.ByteArrayInputStream
import java.io.File


class BrowseFragment : Fragment(), CoroutineScope by MainScope() {

    companion object {
        private const val LOGGING_TAG = "BrowseFragment"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"

        private const val APP_BAR_ACTIONS_DEFAULT = 1
        private const val APP_BAR_ACTIONS_FROM_HTML = 2
        private const val APP_BAR_ACTIONS_GAME_JAM = 3
    }
    
    private var _binding: BrowseFragmentBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var chromeClient: MitchWebChromeClient
    private lateinit var webView: MitchWebView

    private var browseHandler: ItchBrowseHandler? = null
    private var currentDoc: Document? = null
    private var currentInfo: ItchBrowseHandler.Info? = null

    val isWebFullscreen: Boolean
        get() = chromeClient.customViewCallback != null
    val url: String?
        get() = webView.url


    override fun onAttach(context: Context) {
        super.onAttach(context)
        browseHandler = ItchBrowseHandler(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = BrowseFragmentBinding.inflate(inflater, container, false)
        webView = binding.webView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chromeClient = MitchWebChromeClient()

        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
//        webView.settings.setAppCacheEnabled(true)
//        webView.settings.setAppCachePath(File(requireContext().filesDir, "html5-app-cache").path)
        webView.settings.databaseEnabled = true

        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = MitchWebViewClient()
        webView.webChromeClient = chromeClient

        webView.addJavascriptInterface(ItchJavaScriptInterface(this), "mitchCustomJS")

        webView.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
            Log.d(LOGGING_TAG, "Requesting download...")
            launch(Dispatchers.IO) {
                browseHandler?.onDownloadStarted(url, contentDisposition, mimeType,
                    if (contentLength > 0) contentLength else null)
            }
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

                    val alertDialog = AlertDialog.Builder(requireContext())
                        .setTitle(R.string.browser_search)
                        .setView(viewInflated)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            loadUrl(ItchWebsiteUtils.getSearchUrl(input.text.toString()))
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()

                    //Show keyboard automatically
                    input.post {
                        input.isFocusableInTouchMode = true
                        input.requestFocus()

                        input.postDelayed({
                            val inputMethodManager =
                                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                                        as InputMethodManager
                            inputMethodManager.showSoftInput(input,
                                InputMethodManager.SHOW_IMPLICIT)
                        }, 300)
                    }

                    input.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            alertDialog.dismiss()
                            loadUrl(ItchWebsiteUtils.getSearchUrl(input.text.toString()))
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
        val webViewBundle = savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY)
        if (webViewBundle != null) {
            webView.restoreState(webViewBundle)
        } else {
            loadUrl(ItchWebsiteUtils.getMainBrowsePage(requireContext()))
        }
    }

    /**
     * @return true if the user can't go back in the web history
     */
    fun onBackPressed(): Boolean {
        chromeClient.customViewCallback?.let { callback ->
            callback.onCustomViewHidden()
            return@onBackPressed true
        }

        if (webView.canGoBack()) {
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

    override fun onResume() {
        super.onResume()
        
        chromeClient.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

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

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    /**
     * Adapts the app's UI to the theme of the current web page.
     */
    fun updateUI() {
        updateUI(currentDoc, currentInfo)
    }

    fun restoreDefaultUI() {
        updateUI(null, null)
    }

    //TODO: hide stuff on scroll?
    /**
     * Adapts the app's UI to the theme of a web page. Should only affect the UI while the browse
     * fragment is selected.
     * Must run on the UI thread!
     * @param doc the parsed DOM of the page the user is currently on. Null if the UI shouldn't adapt to any web page at all
     * @param info metadata about the page
     */
    private fun updateUI(doc: Document?, info: ItchBrowseHandler.Info?) {
        if (!this::chromeClient.isInitialized || isWebFullscreen)
            return

        val mainActivity = activity as? MainActivity ?: return

        if (!isVisible && doc != null)
            return

        //Log.d(LOGGING_TAG, "Processing UI...")

        val navBar = mainActivity.binding.bottomNavigationView
        val bottomGameBar = mainActivity.binding.bottomGameBar
        val fab = mainActivity.binding.speedDial
        val supportAppBar = mainActivity.supportActionBar!!
        val appBar = mainActivity.binding.toolbar
        val gameButton = mainActivity.binding.gameButton
        val gameButtonInfo = mainActivity.binding.gameButtonInfo

        fab.show()

        if (doc?.let { ItchWebsiteUtils.isGamePage(doc) } == true) {
            //Hide app's navbar after hiding web navbar
            val navBarHideCallback: (String) -> Unit = navBarHide@{
                if (!isVisible)
                    return@navBarHide
                navBar.visibility = View.GONE

                val actions = ArrayList<Triple<String, Spanned, View.OnClickListener>>()
                var filesRequirePayment = false
                if (info?.purchasedInfo?.isNotEmpty() == true) {
                    for (purchasedInfo in info.purchasedInfo) {
                        val buttonText = if (info.hasAndroidVersion)
                            getString(R.string.game_install)
                        else
                            getString(R.string.game_download)
                        val buttonLabel = Utils.spannedFromHtml(purchasedInfo.ownershipReasonHtml)
                        val onButtonClick = View.OnClickListener {
                            goToDownloadOrPurchase(doc, info, purchasedInfo.downloadPage)
                        }
                        actions.add(Triple(buttonText, buttonLabel, onButtonClick))
                    }
                } else if (info?.bundleDownloadLink != null) {
                    val buttonText = getString(R.string.game_bundle_claim)
                    val buttonLabel = SpannedString(getString(resources.getIdentifier(
                        "game_bundle_" + info.specialBundle!!.slug,
                        "string",
                        requireContext().packageName
                    )))
                    val onButtonClick = View.OnClickListener {
                        lifecycleScope.launch {
                            SpecialBundleHandler.claimGame(
                                info.bundleDownloadLink,
                                info.game!!
                            )
                            webView.reload()
                        }
                    }
                    actions.add(Triple(buttonText, buttonLabel, onButtonClick))
                } else if (info?.paymentInfo != null) {
                    val buttonText = if (!info.paymentInfo.isPaymentOptional) {
                        filesRequirePayment = true
                        getString(R.string.game_buy)
                    } else {
                        if (info.hasAndroidVersion)
                            getString(R.string.game_install)
                        else
                            getString(R.string.game_download)
                    }

                    val buttonLabel = Utils.spannedFromHtml(info.paymentInfo.messageHtml)
                    val onButtonClick = View.OnClickListener {
                        val purchaseUri = Uri.parse(info.game!!.storeUrl)
                            .buildUpon()
                            .appendPath("purchase")
                        goToDownloadOrPurchase(doc, info, purchaseUri.toString())
                    }
                    actions.add(Triple(buttonText, buttonLabel, onButtonClick))
                }
                if (info?.game?.webEntryPoint != null && info.webLaunchLabel != null) {
                    val buttonText = info.webLaunchLabel
                    val buttonLabel = SpannedString(getString(R.string.game_web_play_desc))
                    val onButtonClick = View.OnClickListener {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(info.game.webEntryPoint),
                            mainActivity,
                            GameActivity::class.java
                        )
                        intent.putExtra(GameActivity.EXTRA_GAME_ID, info.game.gameId)
                        intent.putExtra(GameActivity.EXTRA_IS_OFFLINE, false)
                        mainActivity.startActivity(intent)
                    }
                    actions.add(Triple(buttonText, buttonLabel, onButtonClick))
                }

                if (actions.size > 1) {
                    bottomGameBar.visibility = View.VISIBLE

                    gameButton.text = getString(R.string.game_multiple_options_get)
                    gameButtonInfo.text = if (info?.game?.webEntryPoint == null)
                        getString(R.string.game_multiple_options_desc_multiple_purchases)
                    else if (filesRequirePayment)
                        getString(R.string.game_multiple_options_desc_web_or_buy)
                    else
                        getString(R.string.game_multiple_options_desc_web_or_download)
                    gameButton.setOnClickListener {
                        val viewInflated: View = LayoutInflater.from(context)
                            .inflate(R.layout.dialog_game_get, view as ViewGroup, false)
                        val dialog = AlertDialog.Builder(requireContext()).run {
                            setTitle(info?.game?.name)
                            setView(viewInflated)
                            show()
                        }
                        val buttonsColumn =
                            viewInflated.findViewById<LinearLayout>(R.id.dialog_game_get_button_column)
                        val labelsColumn =
                            viewInflated.findViewById<LinearLayout>(R.id.dialog_game_get_desc_column)

                        for ((buttonText, label, onButtonClick) in actions) {
                            val button = LayoutInflater.from(context)
                                .inflate(
                                    R.layout.dialog_game_get_button,
                                    view as ViewGroup,
                                    false
                                )
                            (button as Button).apply {
                                text = buttonText
                                setOnClickListener { view ->
                                    dialog.hide()
                                    onButtonClick.onClick(view)
                                }
                                buttonsColumn.addView(this)
                            }

                            val labelView = LayoutInflater.from(context)
                                .inflate(
                                    R.layout.dialog_game_get_label,
                                    view as ViewGroup,
                                    false
                                )
                            labelView.findViewById<TextView>(R.id.game_get_dialog_option_label)
                                .text = label
                            labelsColumn.addView(labelView)
                        }
                    }
                } else if (actions.size == 1) {
                    bottomGameBar.visibility = View.VISIBLE

                    val (text, label, onButtonClick) = actions[0]
                    gameButton.text = text
                    gameButtonInfo.text = label
                    gameButton.setOnClickListener(onButtonClick)
                } else {
                    bottomGameBar.visibility = View.GONE
                }
            }
            if (ItchWebsiteUtils.siteHasNavbar(webView, doc)) {
                setSiteNavbarVisibility(false, navBarHideCallback)
            } else {
                setSiteNavbarVisibility(true, navBarHideCallback)
            }

            supportAppBar.title =
                Utils.spannedFromHtml("<b>${Html.escapeHtml(ItchWebsiteParser.getGameName(doc))}</b>")

            appBar.menu.clear()
            MenuCompat.setGroupDividerEnabled(appBar.menu, true)
            addAppBarActionsFromHtml(appBar, doc)
            addDefaultAppBarActions(appBar)
            supportAppBar.show()
        } else if (doc?.let { ItchWebsiteUtils.isUserPage(it) } == true) {
            val appBarTitle =
                "<b>${Html.escapeHtml(ItchWebsiteParser.getUserName(doc))}</b>"
            supportAppBar.title = Utils.spannedFromHtml(appBarTitle)

            appBar.menu.clear()
            addDefaultAppBarActions(appBar)
            supportAppBar.show()

            bottomGameBar.visibility = View.GONE
            navBar.visibility = View.GONE
        } else if (doc?.let { ItchWebsiteUtils.isJamOrForumPage(it) } == true) {
            val appBarTitle =
                "<b>${Html.escapeHtml(ItchWebsiteParser.getForumOrJamName(doc))}</b>"
            supportAppBar.title = Utils.spannedFromHtml(appBarTitle)

            appBar.menu.clear()
            addDefaultAppBarActions(appBar)
            supportAppBar.show()

            bottomGameBar.visibility = View.GONE
            navBar.visibility = View.GONE
        } else {
            navBar.visibility = View.VISIBLE
            bottomGameBar.visibility = View.GONE
            supportAppBar.hide()
        }

        //Colors adapt to game theme

        val defaultAccentColor = Utils.getColor(requireContext(), R.color.colorAccent)
        val defaultWhiteColor = Utils.getColor(requireContext(), R.color.colorPrimary)
        val defaultBlackColor = Utils.getColor(requireContext(), R.color.colorPrimaryDark)

        val defaultBgColor = Utils.getColor(requireContext(), R.color.colorBackground)
        val defaultFgColor = Utils.getColor(requireContext(), R.color.colorForeground)

        val gameThemeBgColor = doc?.run { ItchWebsiteUtils.getBackgroundUIColor(doc) }
        val gameThemeButtonColor = doc?.run { ItchWebsiteUtils.getAccentUIColor(doc) }
        val gameThemeButtonFgColor = doc?.run { ItchWebsiteUtils.getAccentFgUIColor(doc) }

//        Log.d(LOGGING_TAG, "game theme bg color: $gameThemeBgColor")
//        Log.d(LOGGING_TAG, "game theme button color: $gameThemeButtonColor")
//        Log.d(LOGGING_TAG, "game theme button fg color: $gameThemeButtonFgColor")

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

        bottomGameBar.setBackgroundColor(bgColor)
        gameButtonInfo.setTextColor(defaultWhiteColor)
        gameButton.setTextColor(accentFgColor)
        gameButton.setBackgroundColor(accentColor)

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
     * Go to a game's download or purchase page, and possibly show a warning dialog
     * if the game is not an Android game
     */
    private fun goToDownloadOrPurchase(doc: Document, info: ItchBrowseHandler.Info, url: String) {
        val mainActivity = activity as? MainActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity)

        if (!info.hasAndroidVersion && info.hasWindowsMacOrLinuxVersion
            && prefs.getBoolean(PREF_WARN_WRONG_OS, true)) {

            val platforms = ItchWebsiteParser.getInstallationsPlatforms(doc)

            var foundExtras = false
            for (platformBitmap in platforms) {
                if (platformBitmap == Installation.PLATFORM_NONE) {
                    foundExtras = true
                    break
                }
            }

            val dialog = AlertDialog.Builder(mainActivity).run {
                setTitle(android.R.string.dialog_alert_title)
                setIconAttribute(android.R.attr.alertDialogIcon)

                if (foundExtras) {
                    setMessage(getString(R.string.dialog_download_wrong_os_has_extras, info.game!!.name))
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        mainActivity.browseUrl(url)
                    }
                } else {
                    setMessage(getString(R.string.dialog_download_wrong_os, info.game!!.name))
                    setPositiveButton(R.string.dialog_yes) { _, _ ->
                        mainActivity.browseUrl(url)
                    }
                }
                setNegativeButton(R.string.dialog_no) { _, _ ->
                    //no-op
                }

                create()
            }
            dialog.show()
        } else {
            mainActivity.browseUrl(url)
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
    private fun addAppBarActionsFromHtml(appBar: Toolbar, doc: Document) {
        appBar.menu.clear()

        val navbarItems = doc.getElementById("user_tools")?.children() ?: return

        while (navbarItems.isNotEmpty()) {
            val item = navbarItems.last()!!
            val url = item.child(0).attr("href")

            if (item.getElementsByClass("related_games_btn").isNotEmpty()) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 5, 5, R.string.menu_game_related)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("rate_game_btn").isNotEmpty()) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 4, 4, R.string.menu_game_rate)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }
                    .setIcon(R.drawable.ic_baseline_rate_review_24)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)

            } else if (item.hasClass("devlog_link")) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 3, 3, R.string.menu_game_devlog)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("add_to_collection_btn").isNotEmpty()) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 2, 2, R.string.menu_game_collection)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }

            } else if (item.hasClass("jam_entry")) {
                //TODO: handle multiple jam entries nicely
                val menuItemName = item.child(0).text()

                appBar.menu.add(APP_BAR_ACTIONS_GAME_JAM, 1, 1, menuItemName)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
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

                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 0, 0, menuItemName).setOnMenuItemClickListener {
                    loadUrl(url)
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
        appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 10, 10, R.string.nav_website_view).setOnMenuItemClickListener {
            loadUrl(ItchWebsiteUtils.getMainBrowsePage(requireContext()))
            true
        }
        appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 11, 11, R.string.nav_installed).setOnMenuItemClickListener {
            (activity as MainActivity).setActiveFragment(MainActivity.LIBRARY_FRAGMENT_TAG, true)
            true
        }
        appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 12, 12, R.string.nav_updates).setOnMenuItemClickListener {
            (activity as MainActivity).setActiveFragment(MainActivity.UPDATES_FRAGMENT_TAG, true)
            true
        }
        appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 13, 13, R.string.nav_settings).setOnMenuItemClickListener {
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
    

    @Keep //prevent this class from being removed by compiler optimizations
    private class ItchJavaScriptInterface(val fragment: BrowseFragment) {
        @JavascriptInterface
        fun onDownloadLinkClick(uploadId: String) {
            fragment.launch {
                fragment.browseHandler?.setClickedUploadId(uploadId.toInt())
            }
        }

        @JavascriptInterface
        fun onHtmlLoaded(html: String, url: String) {
            if (fragment.activity !is MainActivity)
                return

            Log.d(LOGGING_TAG, "current info: ${fragment.currentInfo}")

            fragment.launch(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val info = fragment.browseHandler?.onPageVisited(doc, url)
                fragment.currentDoc = doc
                fragment.currentInfo = info
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
        val githubLoginPathRegex = Regex("""/?(login|sessions)(/.*)?""")

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (ItchWebsiteUtils.isItchWebPage(request.url)) {
                return false
            } else if (request.url.host == "github.com"
                    && request.url.path?.matches(githubLoginPathRegex) == true) {
                return false
            } else {
                val intent = Intent(Intent.ACTION_VIEW, request.url)
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Log.i(LOGGING_TAG, "No app found that can handle ${request.url}")
                    Toast.makeText(context,
                        resources.getString(R.string.popup_handler_app_not_found, request.url),
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
                    if (request.url.host?.contains('.' + trackerHostUrl) == true ||
                        request.url.host == trackerHostUrl) {

                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream("tracker_blocked".toByteArray())
                        )
                    }
                }
            }
//            Log.d(LOGGING_TAG, request.url.host)
            return null
        }


        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            view.evaluateJavascript("""
                    document.addEventListener("DOMContentLoaded", (event) => {
                        // tell Android that the document is ready
                        mitchCustomJS.onHtmlLoaded("<html>" + document.getElementsByTagName("html")[0].innerHTML + "</html>",
                                                   window.location.href);
                                               
                        // setup download buttons
                        let downloadButtons = document.getElementsByClassName("download_btn");
                        for (var downloadButton of downloadButtons) {
                            let uploadId = downloadButton.getAttribute("data-upload_id");
                            downloadButton.addEventListener("click", (event) => {
                                mitchCustomJS.onDownloadLinkClick(uploadId);
                            });
                        }
                        
                        // remove YouTube banner
                        let ytBanner = document.querySelector(".youtube_mobile_banner_widget");
                        if (ytBanner)
                            ytBanner.style.visibility = "hidden";
                            
                        // remove game purchase banners, we implement our own
                        let elements = document.querySelectorAll(".purchase_banner, .header_buy_row, .buy_row, .donate_btn, .embed_wrapper, .load_iframe_btn");
                        for (var element of elements)
                            element.style.display = "none";
                            
                        // stop highlighting download links for non-Android OSs
                        const uploads = document.querySelectorAll(".uploads .upload")
                        for (const upload of uploads) {
                        	if (upload.querySelector(".icon-android") != null)
                        		continue
                        	if (upload.querySelector(".icon-windows8, .icon-tux, .icon-apple") == null)
                        		continue
                        	const button = upload.querySelector(".download_btn")
                        	button.setAttribute("style", "background-color: inherit; border-color: #FF2449; color: #FF2449; text-shadow: none;")
                        }
                    });
                    window.addEventListener("resize", (event) => {
                        mitchCustomJS.onResize();
                    });
                """, null
            )

            val uri = Uri.parse(url)
            if (uri.pathSegments.containsAll(listOf("games", "platform-android"))) {
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val androidOnlyFilter = sharedPrefs.getBoolean(PREF_WEB_ANDROID_FILTER, true)
                if (androidOnlyFilter) {
                    webView.evaluateJavascript("""
                    document.addEventListener("DOMContentLoaded", (event) => {
                        // Android-only filter
                        let elements = document.getElementsByClassName("game_cell");
                        for (var element of elements) {
                            if (element.getElementsByClassName("icon-android").length == 0) {
                                element.style.display = "none";
                            }
                        }
                    });
                    """, null)
                }
            }
        }
    }

    inner class MitchWebChromeClient : WebChromeClient() {
        private var customView: View? = null
        private var originalUiVisibility: Int = View.SYSTEM_UI_FLAG_VISIBLE
        var customViewCallback: CustomViewCallback? = null
            private set
        var isForcedFullscreen: Boolean = false
            private set

        override fun onShowCustomView(view: View, callback: CustomViewCallback) =
            this.setFullscreen(view, callback)

        private fun setFullscreen(view: View?, callback: CustomViewCallback) {
            if (view != null)
                webView.visibility = View.GONE

            (activity as? MainActivity)?.apply {
                binding.bottomView.visibility = View.GONE
                binding.speedDial.visibility = View.GONE
                binding.toolbar.visibility = View.GONE

                view?.let { binding.fragmentContainer.addView(it) }

                originalUiVisibility = binding.root.systemUiVisibility
                binding.root.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            (view ?: binding.webView).keepScreenOn = true

            if (view != null)
                customView = view
            else
                isForcedFullscreen = true
            customViewCallback = callback
        }

        override fun onHideCustomView() {
            webView.visibility = View.VISIBLE
            (activity as? MainActivity)?.apply {
                binding.bottomView.visibility = View.VISIBLE

                customView?.let { binding.fragmentContainer.removeView(it) }
                binding.root.systemUiVisibility = originalUiVisibility
            }

            customView = null
            isForcedFullscreen = false
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

        fun onResume() {
            if (isWebFullscreen) {
                binding.root.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }

        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            val dialog = AlertDialog.Builder(requireContext()).apply {
                setTitle(resources.getString(R.string.dialog_web_alert, url))
                setMessage(message)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    result!!.confirm()
                }
                setCancelable(false)

                create()
            }
            dialog.show()
            return true
        }

        override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            val dialog = AlertDialog.Builder(requireContext()).apply {
                setTitle(resources.getString(R.string.dialog_web_prompt, url))
                setMessage(message)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    result!!.confirm()
                }
                setNegativeButton(android.R.string.cancel) { _, _ ->
                    result!!.cancel()
                }
                setOnCancelListener { 
                    result!!.cancel()
                }

                create()
            }
            dialog.show()
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?
        ): Boolean {
            val binding = DialogWebPromptBinding.inflate(layoutInflater)

            val dialog = AlertDialog.Builder(requireContext()).apply {
                setTitle(resources.getString(R.string.dialog_web_prompt, url))
                binding.message.text = message
                binding.input.setText(defaultValue)
                setView(binding.root)

                setPositiveButton(android.R.string.ok) { _, _ ->
                    result!!.confirm(binding.message.text.toString())
                }
                setNegativeButton(android.R.string.cancel) { _, _ ->
                    result!!.cancel()
                }
                setOnCancelListener {
                    result!!.cancel()
                }

                create()
            }
            dialog.show()
            return true
        }
    }
}