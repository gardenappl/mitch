package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.SpannedString
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import com.leinardi.android.speeddial.SpeedDialView.OnChangeListener
import garden.appl.mitch.BuildConfig
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.PREF_DEBUG_WEB_GAMES_IN_BROWSE_TAB
import garden.appl.mitch.PREF_WARN_WRONG_OS
import garden.appl.mitch.PREF_WEB_ANDROID_FILTER
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.client.ItchBrowseHandler
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.client.SpecialBundleHandler
import garden.appl.mitch.data.ItchGenre
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.databinding.BrowseFragmentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.security.SecureRandom
import java.util.Locale


class BrowseFragment : Fragment(), CoroutineScope by MainScope() {
    companion object {
        private const val LOGGING_TAG = "BrowseFragment"
        private const val WEB_VIEW_STATE_KEY: String = "WebView"
        private const val GENRES_EXCLUSION_FILTER: String = "GenresFilter"

        private const val APP_BAR_ACTIONS_DEFAULT = 1
        private const val APP_BAR_ACTIONS_FROM_HTML = 2
        private const val APP_BAR_ACTIONS_GAME_JAM = 3
    }
    
    private var _binding: BrowseFragmentBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var chromeClient: MitchBrowserWebChromeClient
    private lateinit var webView: MitchWebView
    private var webViewJSNonce: Long = 0

    private var browseHandler: ItchBrowseHandler? = null
    private var currentDoc: Document? = null
    private var currentInfo: ItchBrowseHandler.Info? = null

    val isWebFullscreen: Boolean
        get() = chromeClient.customViewCallback != null
    val url: String?
        get() = webView.url

    /**
     * Game genres to hide from a catalogue page.
     * Set to null (and subsequently the list is forgotten) when we navigate to a non-catalogue page
     */
    private var genresExclusionFilter: Set<ItchGenre>? = null

    private val openDocumentLauncher = registerForActivityResult(OpenDocument()) { uri ->
        uri?.let { filePathCallback?.onReceiveValue(arrayOf(it)) }
        filePathCallback = null
    }
    private val openMultipleDocumentsLauncher = registerForActivityResult(OpenMultipleDocuments()) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        browseHandler = ItchBrowseHandler(context as MainActivity, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        genresExclusionFilter = savedInstanceState?.getStringArray(GENRES_EXCLUSION_FILTER)?.map {
            ItchGenre.valueOf(it)
        }?.toSet()
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

        chromeClient = MitchBrowserWebChromeClient()

        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
//        webView.settings.setAppCacheEnabled(true)
//        webView.settings.setAppCachePath(File(requireContext().filesDir, "html5-app-cache").path)
        webView.settings.databaseEnabled = true

        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = MitchBrowserWebViewClient(this)
        webView.webChromeClient = chromeClient

        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webViewJSNonce = SecureRandom().nextLong()
        // JavaScript interface has catastrophic security vulnerabilities in old Android versions.
        // Explicitly disable it even though minSdk is greater than JellyBean.
        @SuppressLint("ObsoleteSdkInt")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN)
            webView.addJavascriptInterface(MitchJavaScriptInterface(this), "mitchCustomJS")

        webView.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
            Log.d(LOGGING_TAG, "Requesting download...")
            launch(Dispatchers.IO) {
                browseHandler?.onDownloadStarted(url, contentDisposition, mimeType,
                    if (contentLength > 0) contentLength else null)
            }
        }

        webView.setOnLongClickListener { _ ->
            val result = webView.hitTestResult
            val url = result.extra ?: return@setOnLongClickListener false
            when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                    val data = ClipData.newPlainText("Copied URL", url)
                    (clipboard as ClipboardManager).setPrimaryClip(data)
                    Toast.makeText(requireContext(), R.string.popup_link_copied, Toast.LENGTH_LONG)
                        .show()
                    return@setOnLongClickListener true
                }
                else -> return@setOnLongClickListener false
            }
        }


        //Set up FAB buttons
        //(colors don't matter too much as they will be set by updateUI() anyway)
        val speedDial = (activity as MainActivity).binding.speedDial
        speedDial.clearActionItems()
        speedDial.addActionItem(SpeedDialActionItem.Builder(R.id.browser_reload, R.drawable.ic_baseline_refresh_24)
            .setLabel(R.string.browser_reload)
            .create()
        )
        speedDial.addActionItem(SpeedDialActionItem.Builder(R.id.browser_search, R.drawable.ic_baseline_search_24)
            .setLabel(R.string.browser_search)
            .create()
        )
        speedDial.addActionItem(SpeedDialActionItem.Builder(R.id.browser_open_in_browser, R.drawable.ic_baseline_open_in_browser_24)
            .setLabel(R.string.browser_open_in_browser)
            .create()
        )
        speedDial.addActionItem(SpeedDialActionItem.Builder(R.id.browser_share, R.drawable.ic_baseline_share_24)
            .setLabel(R.string.browser_share)
            .create()
        )
        
        speedDial.setOnActionSelectedListener { actionItem ->
            speedDial.close()

            when (actionItem.id) {
                R.id.browser_reload -> {
                    webView.reload()
                    return@setOnActionSelectedListener true
                }
                R.id.browser_share -> {
                    ShareCompat.IntentBuilder.from(requireActivity())
                        .setType("text/plain")
                        .setChooserTitle(R.string.browser_share)
                        .setText(url)
                        .startChooser()
                    return@setOnActionSelectedListener true
                }
                // https://stackoverflow.com/questions/2201917/how-can-i-open-a-url-in-androids-web-browser-from-my-application#61488105
                R.id.browser_open_in_browser -> {
                    val resolveIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
                    val resolveInfo = requireContext().packageManager
                        .resolveActivity(resolveIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    val defaultBrowserPackageName = resolveInfo?.activityInfo?.packageName

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(url)

                    Log.d(LOGGING_TAG, "Default browser: $defaultBrowserPackageName")
                    if (defaultBrowserPackageName == null ||
                        defaultBrowserPackageName == "android") {
                        // "android" means no default browser is set
                        val title = resources.getString(R.string.browser_open_in_browser)
                        startActivity(Intent.createChooser(intent, title))
                    } else {
                        intent.setPackage(defaultBrowserPackageName)
                        startActivity(intent)
                    }
                    return@setOnActionSelectedListener true
                }
                R.id.browser_search -> {
                    // Search dialog

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

                    // Show keyboard automatically
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
                R.id.browser_filter_exclude_genres -> {
                    data class GenreChoice(val genre: ItchGenre) {
                        override fun toString() = requireContext().getString(genre.nameResource)
                    }
                    val choices = ItchGenre.entries.map { GenreChoice(it) }

                    val adapter = ArrayAdapter(requireContext(),
                        android.R.layout.simple_list_item_multiple_choice, choices)
                    val newExclusionFilter = genresExclusionFilter?.toMutableSet()
                        ?: return@setOnActionSelectedListener false

                    val listView = ListView(context).apply {
                        choiceMode = ListView.CHOICE_MODE_MULTIPLE
                        setOnItemClickListener { parent, view, position, id ->
                            val choice = parent.getItemAtPosition(position) as GenreChoice
                            if (this@apply.isItemChecked(position))
                                newExclusionFilter.add(choice.genre)
                            else
                                newExclusionFilter.remove(choice.genre)
                        }
                        this.adapter = adapter
                    }

                    val dialog = AlertDialog.Builder(requireContext()).run {
                        setTitle(R.string.browser_filter_exclude_genres)
                        setView(listView)
                        setPositiveButton(R.string.dialog_apply) { _, _ ->
                            genresExclusionFilter = newExclusionFilter
                            updateUI()
                        }
                        setNegativeButton(R.string.dialog_reset) { _, _ ->
                            genresExclusionFilter = emptySet()
                            updateUI()
                        }
                        create()
                    }
                    for (excludedGenre in newExclusionFilter) {
                        listView.setItemChecked(adapter.getPosition(GenreChoice(excludedGenre)), true)
                    }
                    dialog.show()
                    return@setOnActionSelectedListener true
                }
                else -> {
                    return@setOnActionSelectedListener false
                }
            }
        }

        // Load page, this will also update the UI
        val webViewBundle = savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY)
        Utils.logDebug(LOGGING_TAG, "Restoring $webViewBundle")
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
        outState.putStringArray(GENRES_EXCLUSION_FILTER, genresExclusionFilter?.map {
            it.name
        }?.toTypedArray())

        val webViewState = Bundle()
        webView.saveState(webViewState)
        outState.putBundle(WEB_VIEW_STATE_KEY, webViewState)

        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()

        webView.onPause()
//        webView.pauseTimers()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()

        webView.onResume()
//        webView.resumeTimers()
        chromeClient.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
        webView.destroy()
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

    // TODO: hide stuff on scroll?
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

        val navBar = mainActivity.binding.bottomNavigationView
        val bottomGameBar = mainActivity.binding.bottomGameBar
        val speedDial = mainActivity.binding.speedDial
        val supportAppBar = mainActivity.supportActionBar!!
        val appBar = mainActivity.binding.toolbar
        val gameButton = mainActivity.binding.gameButton
        val gameButtonInfo = mainActivity.binding.gameButtonInfo

        updateGenreFilterAndAction(speedDial)
        filterExcludedGenres()

        if (doc?.let { ItchWebsiteUtils.isGamePage(doc) } == true) {
            // Hide app's navbar after hiding web navbar
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
                            mainActivity.browseUrl(purchasedInfo.downloadPage)
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
                        goToPurchasePage(doc, info, purchaseUri.toString())
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
                        intent.putExtra(GameActivity.EXTRA_LAUNCHED_FROM_INSTALL, false)
                        Log.d(LOGGING_TAG, "Starting $intent")
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

        // Colors adapt to game theme

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

        speedDial.mainFabClosedBackgroundColor = accentColor
        speedDial.mainFabOpenedBackgroundColor = accentColor
        speedDial.mainFabClosedIconColor = accentFgColor
        speedDial.mainFabOpenedIconColor = accentFgColor
        speedDial.setOnChangeListener(object : OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                // NO-OP
                return false
            }

            override fun onToggleChanged(isOpen: Boolean) {
                speedDial.elevation = resources.getDimension(
                    if (isOpen)
                        R.dimen.fab_elevation_open
                    else
                        R.dimen.fab_elevation_closed
                )
            }
        })
        for (actionItem in speedDial.actionItems) {
            val newActionItem = SpeedDialActionItem.Builder(actionItem)
                .setFabBackgroundColor(bgColor)
                .setFabImageTintColor(fgColor)
                .setLabelBackgroundColor(bgColor)
                .setLabelColor(fgColor)
                .create()
            speedDial.replaceActionItem(actionItem, newActionItem)
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

    private fun updateGenreFilterAndAction(speedDial: SpeedDialView) {
        if (url == null)
            return
        val uri = Uri.parse(url)
        if (ItchWebsiteUtils.isGameCataloguePage(uri)) {
            val genreExcludeSet = genresExclusionFilter ?: emptySet<ItchGenre>().also {
                genresExclusionFilter = emptySet()
            }
            speedDial.addActionItem(SpeedDialActionItem.Builder(R.id.browser_filter_exclude_genres, R.drawable.ic_baseline_filter_alt_24).run {
                if (genreExcludeSet.isEmpty())
                    setLabel(R.string.browser_filter_exclude_genres)
                else
                    setLabel(resources.getQuantityString(R.plurals.browser_filter_exclude_genres_active,
                        genreExcludeSet.size, genreExcludeSet.size))
                create()
            })
        } else {
            genresExclusionFilter = null
            speedDial.removeActionItemById(R.id.browser_filter_exclude_genres)
        }
        speedDial.show()
    }

    /**
     * Go to a game's purchase page, and possibly show a warning dialog
     * if the game is not an Android game
     */
    private fun goToPurchasePage(doc: Document, info: ItchBrowseHandler.Info, url: String) {
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

                val message = if (foundExtras)
                    R.string.dialog_purchase_wrong_os_has_extras
                else
                    R.string.dialog_purchase_wrong_os
                setMessage(getString(message, info.game!!.name))

                val positiveButton = if (foundExtras) android.R.string.ok else R.string.dialog_yes
                val negativeButton = if (foundExtras) android.R.string.cancel else R.string.dialog_no
                setPositiveButton(positiveButton) { _, _ ->
                    mainActivity.browseUrl(url)
                }
                setNegativeButton(negativeButton) { _, _ ->
                    // no-op
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
     * Also adds a Subscription button for game URLS.
     *
     * Should run on the UI thread!
     *
     * @param doc the parsed HTML document of a game store page or devlog page
     * @param appBar the app UI's top Toolbar
     */
    private fun addAppBarActionsFromHtml(appBar: Toolbar, doc: Document) {
        appBar.menu.clear()

        if (ItchWebsiteUtils.isStorePage(doc) || ItchWebsiteUtils.isDownloadPage(doc)) {
            appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 0, 0, R.string.menu_game_subscribe)
                .setOnMenuItemClickListener {
                    this.launch {
                        showSubscriptionDialog(doc)
                    }
                    true
                }
        }

        val navbarItems = doc.getElementById("user_tools")?.children() ?: return

        while (navbarItems.isNotEmpty()) {
            val item = navbarItems.last()!!
            val url = item.child(0).attr("href")

            if (item.getElementsByClass("related_games_btn").isNotEmpty()) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 7, 7, R.string.menu_game_related)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("rate_game_btn").isNotEmpty()) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 6, 6, R.string.menu_game_rate)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }
                    .setIcon(R.drawable.ic_baseline_rate_review_24)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)

            } else if (item.hasClass("devlog_link")) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 5, 5, R.string.menu_game_devlog)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("add_to_collection_btn").isNotEmpty()) {
                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 4, 4, R.string.menu_game_collection)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }

            } else if (item.getElementsByClass("view_more").isNotEmpty()) {
                // Cannot rely on ItchWebsiteParser, because its method requires the current URL,
                // and while loading another page, url changes prematurely
                // (leading to crashes...)
                val authorName = item.getElementsByClass("mobile_label")[0].text()

                val menuItemName =
                    if (item.getElementsByClass("full_label")[0].text().contains(authorName))
                        resources.getString(R.string.menu_game_author, authorName)
                    else
                        resources.getString(R.string.menu_game_author_generic)

                appBar.menu.add(APP_BAR_ACTIONS_FROM_HTML, 3, 3, menuItemName).setOnMenuItemClickListener {
                    loadUrl(url)
                    true
                }
            } else if (item.hasClass("jam_entry")) {
                // TODO: handle multiple jam entries nicely
                val menuItemName = item.child(0).text()

                appBar.menu.add(APP_BAR_ACTIONS_GAME_JAM, 0, 0, menuItemName)
                    .setOnMenuItemClickListener {
                        loadUrl(url)
                        true
                    }
                    .setIcon(R.drawable.ic_baseline_emoji_events_24)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)

            }

            navbarItems.removeAt(navbarItems.size - 1)
        }
    }

    private suspend fun showSubscriptionDialog(doc: Document) {
        val installations = if (ItchWebsiteUtils.hasGameDownloadLinks(doc)) {
            ItchWebsiteParser.getInstallations(doc)
        } else {
            val downloadUrl = url?.let {
                ItchWebsiteParser.getDownloadUrl(doc, it)?.url
            }
            if (downloadUrl == null) {
                Toast.makeText(context, R.string.popup_subscribe_game_not_owned, Toast.LENGTH_LONG)
                    .show()
                return
            }
            ItchWebsiteParser.getInstallations(ItchWebsiteUtils.fetchAndParse(downloadUrl))
        }
        val db = AppDatabase.getDatabase(requireContext())
        val subscriptions = db.installDao.getFinishedInstallationsAndSubscriptionsSync()
        val availableSubscriptions =
            installations.filter { install ->
                !subscriptions.any { subscription -> subscription.uploadId == install.uploadId }
            }
        if (availableSubscriptions.isEmpty()) {
            Toast.makeText(context, R.string.popup_subscribe_game_all_subscribed, Toast.LENGTH_LONG)
                .show()
            return
        }
        val subscribeOptions = availableSubscriptions.map { install ->
            val platforms = install.platformsStrings
            if (platforms.isEmpty())
                return@map install.uploadName
            else
                return@map "(${platforms.joinToString()}) ${install.uploadName}"
        }.toTypedArray()
        Log.d(LOGGING_TAG, subscribeOptions.joinToString())
        AlertDialog.Builder(requireContext()).run {
            setTitle(R.string.dialog_subscribe_title)
//            setMessage(R.string.dialog_subscribe_message)
            setMultiChoiceItems(subscribeOptions, null) { _, _, _ -> /* NO-OP */ }
            setPositiveButton(R.string.dialog_subscribe_yes) { dialog, _ ->
                val checkedPositions = (dialog as AlertDialog).listView.checkedItemPositions
                val selectedSubscriptions = availableSubscriptions
                    .filterIndexed{ index, _ -> checkedPositions.get(index) }
                this@BrowseFragment.launch {
                    for (subscription in selectedSubscriptions) {
                        db.installDao.insert(subscription.copy(
                            status = Installation.STATUS_SUBSCRIPTION
                        ))
                    }
                    if (subscriptions.isNotEmpty())
                        (activity as MainActivity).setActiveFragment(MainActivity.UPDATES_FRAGMENT_TAG)
                }
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> /* NO-OP */ }
            setCancelable(true)
            show()
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
            (activity as MainActivity).setActiveFragment(MainActivity.LIBRARY_FRAGMENT_TAG)
            true
        }
        appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 12, 12, R.string.nav_updates).setOnMenuItemClickListener {
            (activity as MainActivity).setActiveFragment(MainActivity.UPDATES_FRAGMENT_TAG)
            true
        }
        appBar.menu.add(APP_BAR_ACTIONS_DEFAULT, 13, 13, R.string.nav_settings).setOnMenuItemClickListener {
            (activity as MainActivity).setActiveFragment(MainActivity.SETTINGS_FRAGMENT_TAG)
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

    private fun filterExcludedGenres() {
        val excludeSet = genresExclusionFilter ?: return

        val englishContext = Utils.makeLocalizedContext(requireContext(), Locale.ENGLISH)

        val excludeString = excludeSet.joinToString(prefix = "[", postfix = "]") { genre ->
            val englishName = englishContext.getString(genre.nameResource)
            if (englishName.contains('"'))
                throw IllegalStateException("Bad English translation!")
            return@joinToString "\"$englishName\""
        }

        Log.d(LOGGING_TAG, "Exclusion filter array: $excludeString")

        webView.post {
            webView.evaluateJavascript("""
                {
                	const excludeFilter = $excludeString
                    const gameGrid = document.querySelector(".browse_game_grid")

                	for (const gameCell of gameGrid.getElementsByClassName("game_cell")) {
                		const genre = gameCell.querySelector(".game_genre")

                		if (genre && excludeFilter.includes(genre.textContent)) {
                			gameCell.setAttribute("style", "display: none")
                			gameCell.setAttribute("data-mitch-excluded-genre", "true")
                		} else if (gameCell.hasAttribute("data-mitch-excluded-genre")) {
                			gameCell.removeAttribute("style")
                			gameCell.removeAttribute("data-mitch-excluded-genre")
                		}
                    }
                }
                """.trimIndent(), null
            )
        }
    }
    

    @Keep // prevent this class from being removed by compiler optimizations
    private class MitchJavaScriptInterface(val fragment: BrowseFragment) {
        private fun verifyNonce(nonce: String) {
            if (nonce != fragment.webViewJSNonce.toString()) {
                fragment.launch(Dispatchers.Main) {
                    throw SecurityException("Wrong nonce in JavaScript interface call. Expected ${fragment.webViewJSNonce}, got $nonce")
                }
            }
        }

        @JavascriptInterface
        fun onDownloadLinkClick(uploadId: String, nonce: String) {
            verifyNonce(nonce)
            fragment.launch {
                fragment.browseHandler?.setClickedUploadId(uploadId.toInt())
            }
        }

        @JavascriptInterface
        fun onHtmlLoaded(html: String, url: String, nonce: String) {
            verifyNonce(nonce)
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

    inner class MitchBrowserWebViewClient(
        private val browseFragment: BrowseFragment
    ) : MitchWebViewClient() {
        val githubLoginPathRegex = Regex("""^/?(login|sessions)(/.*)?$""")

        override fun shouldOverrideUrlLoading(view: WebView, uri: Uri): Boolean {
            if (uri.host == "github.com"
                && uri.path?.matches(githubLoginPathRegex) == true) {
                return false
            }

            return super.shouldOverrideUrlLoading(view, uri)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(browseFragment.requireContext())
            val hiddenElements = if (prefs.getBoolean(PREF_DEBUG_WEB_GAMES_IN_BROWSE_TAB, false) && BuildConfig.DEBUG)
                ".purchase_banner, .header_buy_row, .buy_row, .donate_btn"
            else
                ".purchase_banner, .header_buy_row, .buy_row, .donate_btn, .embed_wrapper, .load_iframe_btn"
            view.evaluateJavascript("""
                document.addEventListener("DOMContentLoaded", (event) => {
                    // tell Android that the document is ready
                    mitchCustomJS.onHtmlLoaded("<html>" + document.getElementsByTagName("html")[0].innerHTML + "</html>",
                                               window.location.href, "${browseFragment.webViewJSNonce}");
                                           
                    // setup download buttons
                    let downloadButtons = document.getElementsByClassName("download_btn");
                    for (var downloadButton of downloadButtons) {
                        let uploadId = downloadButton.getAttribute("data-upload_id");
                        downloadButton.addEventListener("click", (event) => {
                            mitchCustomJS.onDownloadLinkClick(uploadId, "${browseFragment.webViewJSNonce}");
                        });
                    }
                    
                    // remove YouTube banner
                    let ytBanner = document.querySelector(".youtube_mobile_banner_widget");
                    if (ytBanner)
                        ytBanner.style.visibility = "hidden";
                        
                    // remove game purchase banners, we implement our own
                    let elements = document.querySelectorAll("$hiddenElements");
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
                        if (!button)
                            continue
                        let buttonColor = getComputedStyle(button).getPropertyValue("--itchio_button_color")
                        if (!buttonColor)
                            buttonColor = '#FF2449'
                        button.setAttribute("style", "background-color: inherit; border-color: " 
                                + buttonColor + "; color: " + buttonColor + "; text-shadow: none;")
                    }
                });
                window.addEventListener("resize", (event) => {
                    mitchCustomJS.onResize();
                });
                """, null
            )

            val uri = url.toUri()
            if (uri.pathSegments.containsAll(listOf("games", "platform-android"))) {
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(browseFragment.requireContext())
                val androidOnlyFilter = sharedPrefs.getBoolean(PREF_WEB_ANDROID_FILTER, true)
                if (androidOnlyFilter) {
                    browseFragment.webView.evaluateJavascript("""
                        document.addEventListener("DOMContentLoaded", (event) => {
                            // Android-only filter
                            let elements = document.getElementsByClassName("game_cell");
                            for (var element of elements) {
                                if (element.getElementsByClassName("icon-android").length == 0) {
                                    element.style.display = "none";
                                }
                            }
                        });
                        """, null
                    )
                }
            }
            super.onPageStarted(view, url, favicon)
        }
    }

    inner class MitchBrowserWebChromeClient : MitchWebChromeClient(
        openDocumentLauncher,
        openMultipleDocumentsLauncher
    ) {
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

        override fun setFileChooserCallback(callback: ValueCallback<Array<Uri>>) {
            this@BrowseFragment.filePathCallback = callback
        }
    }
}