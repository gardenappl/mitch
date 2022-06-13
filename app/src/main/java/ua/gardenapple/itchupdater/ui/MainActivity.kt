package ua.gardenapple.itchupdater.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.databinding.ActivityMainBinding

/**
 * The [MainActivity] handles a lot of things, including day/night themes and languages
 */
class MainActivity : MitchActivity(), CoroutineScope by MainScope(),
    ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var browseFragment: BrowseFragment
    private lateinit var currentFragmentTag: String

    lateinit var binding: ActivityMainBinding
    private set

    val runningCachedWebGame: Game?
        get() = browseFragment.runningCachedWebGame

    /**
     * The restart dialog is handled here.
     * Yes, this means that if the language changes outside of MainActivity,
     * then there will be no dialog.
     */
    private val langChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener langChange@{ prefs, key ->
            if (key != PREF_LANG_LOCALE_NEXT)
                return@langChange

            val currentLocale = prefs.getString(PREF_LANG_LOCALE, "null?")
            val nextLocale = prefs.getString(PREF_LANG_LOCALE_NEXT, "null???")
            Log.d(LOGGING_TAG, "handling locale change to $nextLocale, currently $currentLocale")
            if (nextLocale == currentLocale)
                return@langChange

            AlertDialog.Builder(this).run {
                setTitle(R.string.dialog_lang_restart_title)
                setMessage(R.string.dialog_lang_restart)

                setPositiveButton(android.R.string.ok) { _, _ ->
                    this@MainActivity.finish()
                    //Use 'post' method to make sure that Activity lifecycle events
                    //run before the process exits
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.post {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(browseFragment.url),
                            applicationContext,
                            MainActivity::class.java
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                }
                setNegativeButton(android.R.string.cancel) { _, _ -> /* No-op */ }

                show()
            }
        }

    companion object {
        const val LOGGING_TAG = "MainActivity"

        const val EXTRA_SHOULD_OPEN_LIBRARY = "SHOULD_OPEN_LIBRARY"
//        const val EXTRA_LAUNCH_OFFLINE_WEB_GAME_ID = "LAUNCH_OFFLINE_WEB_GAME_ID"
        
        private const val ACTIVE_FRAGMENT_KEY: String = "fragment"

        const val BROWSE_FRAGMENT_TAG: String = "browse"
        const val LIBRARY_FRAGMENT_TAG: String = "library"
        const val SETTINGS_FRAGMENT_TAG: String = "settings"
        const val UPDATES_FRAGMENT_TAG: String = "updates"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Initially set to SplashScreenTheme during loading, this sets the proper theme
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Add app bar, hidden by default
        setSupportActionBar(binding.toolbar)
        supportActionBar!!.hide()


        currentFragmentTag = savedInstanceState?.getString(ACTIVE_FRAGMENT_KEY) ?: BROWSE_FRAGMENT_TAG

        //Fragments aren't destroyed on configuration changes
        
        val tryBrowseFragment = supportFragmentManager.findFragmentByTag(BROWSE_FRAGMENT_TAG)
        if (tryBrowseFragment != null) {
            browseFragment = tryBrowseFragment as BrowseFragment
        } else {
            browseFragment = BrowseFragment()
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, browseFragment, BROWSE_FRAGMENT_TAG)
                if (currentFragmentTag != BROWSE_FRAGMENT_TAG)
                    hide(browseFragment)
                commit()
            }
        }

        if (currentFragmentTag == LIBRARY_FRAGMENT_TAG &&
                supportFragmentManager.findFragmentByTag(LIBRARY_FRAGMENT_TAG) == null) {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, LibraryFragment(), LIBRARY_FRAGMENT_TAG)
                commit()
            }
        }

        if (currentFragmentTag == UPDATES_FRAGMENT_TAG && 
                supportFragmentManager.findFragmentByTag(UPDATES_FRAGMENT_TAG) == null) {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, SettingsFragment(), SETTINGS_FRAGMENT_TAG)
                commit()
            }
        }

        if (currentFragmentTag == SETTINGS_FRAGMENT_TAG &&
                supportFragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG) == null) {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, SettingsFragment(), SETTINGS_FRAGMENT_TAG)
                commit()
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val newFragmentTag = if (browseFragment.isVisible)
                BROWSE_FRAGMENT_TAG
            else if (supportFragmentManager.findFragmentByTag(LIBRARY_FRAGMENT_TAG)?.isVisible == true)
                LIBRARY_FRAGMENT_TAG
            else if (supportFragmentManager.findFragmentByTag(UPDATES_FRAGMENT_TAG)?.isVisible == true)
                UPDATES_FRAGMENT_TAG
            else if (supportFragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG)?.isVisible == true)
                SETTINGS_FRAGMENT_TAG
            else
                throw IllegalStateException("No active fragments?")
            onFragmentSet(newFragmentTag, true)
        }

        val navView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        navView.setOnNavigationItemSelectedListener { item ->
            val fragmentChanged = setActiveFragment(getFragmentTag(item.itemId), false)

            if (!fragmentChanged && currentFragmentTag == BROWSE_FRAGMENT_TAG)
                browseFragment.loadUrl(ItchWebsiteUtils.getMainBrowsePage(this))

            return@setOnNavigationItemSelectedListener fragmentChanged
        }

        setActiveFragment(currentFragmentTag, true)
    }

    override fun onStart() {
        super.onStart()

        if (intent.action == Intent.ACTION_VIEW &&
                intent.data?.let { ItchWebsiteUtils.isItchWebPage(it) } == true) {
            browseUrl(intent.data.toString())
        } else if (intent.getBooleanExtra(EXTRA_SHOULD_OPEN_LIBRARY, false)) {
            setActiveFragment(LIBRARY_FRAGMENT_TAG)
        } else {
//            val launchOfflineWebGameId = intent.getIntExtra(EXTRA_LAUNCH_OFFLINE_WEB_GAME_ID, -1)
//            if (launchOfflineWebGameId != -1)
//                launchWebGame(launchOfflineWebGameId)
        }

        launch {
            // Force lazy-init database to fully initialize, in the background
            val db = AppDatabase.getDatabase(this@MainActivity)
            if (!db.isOpen)
                db.installDao.getInstallationByPackageName(packageName)
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(langChangeListener)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(langChangeListener)
    }

    override fun onBackPressed() {
        if (browseFragment.isVisible) {
            val cantGoBack = browseFragment.onBackPressed()
            if (cantGoBack)
                finish()
            return
        }
        //super method handles fragment back stack
        super.onBackPressed()
    }



    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ACTIVE_FRAGMENT_KEY, currentFragmentTag)
    }

    // Handle light/dark theme changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val backgroundColor = Utils.getColor(this, R.color.colorBackground)
        val backgroundMainColor = Utils.getColor(this, R.color.colorBackgroundMain)
        val foregroundColor = Utils.getColor(this, R.color.colorForeground)
        val accentColor = Utils.getColor(this, R.color.colorAccent)

        val itemColorStateList = Utils.colorStateListOf(
            intArrayOf(android.R.attr.state_selected) to accentColor,
            intArrayOf() to foregroundColor
        )

        binding.bottomNavigationView.apply {
            setBackgroundColor(backgroundColor)
            itemBackground = ColorDrawable(backgroundColor)
            itemIconTintList = itemColorStateList
            itemTextColor = itemColorStateList
        }
        binding.mainLayout.setBackgroundColor(backgroundMainColor)

        //Handle system bar color
        if (browseFragment.isVisible) {
            //BrowseFragment has special handling
            browseFragment.updateUI()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = backgroundColor

                val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    /**
     * @param newFragmentTag one of: [BROWSE_FRAGMENT_TAG], [LIBRARY_FRAGMENT_TAG] etc
     * @param resetNavBar forcibly change the highlighted option in the bottom navigation bar
     * @return true if the current fragment has changed
     */
    fun setActiveFragment(newFragmentTag: String, resetNavBar: Boolean = true): Boolean {
        if (newFragmentTag == currentFragmentTag)
            return false

        supportFragmentManager.beginTransaction().apply {
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            if (currentFragmentTag == BROWSE_FRAGMENT_TAG)
                hide(browseFragment)
            else
                remove(supportFragmentManager.findFragmentByTag(currentFragmentTag)!!)

            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            if (newFragmentTag == BROWSE_FRAGMENT_TAG)
                show(browseFragment)
            else
                add(R.id.fragmentContainer, getFragmentClass(newFragmentTag), Bundle.EMPTY, newFragmentTag)

            addToBackStack(null)

            commit()
        }

        onFragmentSet(newFragmentTag, resetNavBar)

        return true
    }

    fun browseUrl(url: String) {
        setActiveFragment(BROWSE_FRAGMENT_TAG)
        browseFragment.loadUrl(url)
    }

//    private fun launchWebGame(gameId: Int) = launch {
//        val db = AppDatabase.getDatabase(this@MainActivity)
//        val game = db.gameDao.getGameById(gameId)!!
//        launchWebGame(db.gameDao.getGameById(gameId)!!)
//    }

    fun launchWebGame(game: Game) {
        setActiveFragment(BROWSE_FRAGMENT_TAG)
        browseFragment.launchWebGame(game)
    }

    private fun onFragmentSet(newFragmentTag: String, resetNavBar: Boolean) {
        if (resetNavBar)
            navBarSelectItem(getItemId(newFragmentTag))


        if (currentFragmentTag == BROWSE_FRAGMENT_TAG && newFragmentTag != BROWSE_FRAGMENT_TAG)
            browseFragment.restoreDefaultUI()

        currentFragmentTag = newFragmentTag

        if (newFragmentTag == BROWSE_FRAGMENT_TAG) {
            browseFragment.updateUI()
            binding.speedDial.show()
        } else {
            binding.speedDial.hide()
        }
    }

    private fun navBarSelectItem(itemId: Int) {
        binding.bottomNavigationView.post {
            val menu = binding.bottomNavigationView.menu
            
            for (index in 0 until menu.size()) {
                val item = binding.bottomNavigationView.menu.getItem(index)
                if (item.itemId == itemId) {
                    item.isChecked = true
                    break
                }
            }
        }
    }

    private fun getItemId(tag: String): Int {
        return when (tag) {
            BROWSE_FRAGMENT_TAG -> R.id.navigation_website_view
            LIBRARY_FRAGMENT_TAG -> R.id.navigation_library
            UPDATES_FRAGMENT_TAG -> R.id.navigation_updates
            SETTINGS_FRAGMENT_TAG -> R.id.navigation_settings
            else -> throw IllegalArgumentException()
        }
    }
    
    private fun getFragmentTag(itemId: Int): String {
        return when (itemId) {
            R.id.navigation_website_view -> BROWSE_FRAGMENT_TAG
            R.id.navigation_library -> LIBRARY_FRAGMENT_TAG
            R.id.navigation_updates -> UPDATES_FRAGMENT_TAG
            R.id.navigation_settings -> SETTINGS_FRAGMENT_TAG
            else -> throw IllegalArgumentException()
        }
    }
    
    private fun getFragmentClass(tag: String): Class<out Fragment> {
        return when (tag) {
            BROWSE_FRAGMENT_TAG -> BrowseFragment::class.java
            LIBRARY_FRAGMENT_TAG -> LibraryFragment::class.java
            UPDATES_FRAGMENT_TAG -> UpdatesFragment::class.java
            SETTINGS_FRAGMENT_TAG -> SettingsFragment::class.java
            else -> throw IllegalArgumentException()
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_MOVE_TO_DOWNLOADS ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeMoveToDownloads()
            PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeGetViewIntent(this)
        }
    }
}
