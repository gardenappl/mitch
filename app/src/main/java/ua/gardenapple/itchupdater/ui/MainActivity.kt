package ua.gardenapple.itchupdater.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.databinding.ActivityMainBinding
import ua.gardenapple.itchupdater.databinding.DialogDeprecateBinding
import java.time.Instant


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var browseFragment: BrowseFragment
    private lateinit var currentFragmentTag: String

    lateinit var binding: ActivityMainBinding
    private set

    companion object {
        const val LOGGING_TAG = "MainActivity"
        
        const val EXTRA_SHOULD_OPEN_LIBRARY = "SHOULD_OPEN_LIBRARY"
        
        private const val ACTIVE_FRAGMENT_KEY: String = "fragment"

        const val BROWSE_FRAGMENT_TAG: String = "browse"
        const val LIBRARY_FRAGMENT_TAG: String = "library"
        const val SETTINGS_FRAGMENT_TAG: String = "settings"
        const val UPDATES_FRAGMENT_TAG: String = "updates"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Force database to fully initialize while splash screen is on
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            if (!db.isOpen)
                db.gameDao.getGameById(Game.MITCH_GAME_ID)
        }
        
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
                browseFragment.webView.loadUrl(ItchWebsiteUtils.getMainBrowsePage(this))

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
        }

        //TODO: remove dialog for Gitlab
        if (BuildConfig.FLAVOR != FLAVOR_GITLAB)
            return

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (sharedPrefs.getBoolean(PREF_DONT_SHOW_DEPRECATION_DIALOG, false))
            return
        
        val dialog = AlertDialog.Builder(this).apply {
            setTitle("Announcement")
            val binding = DialogDeprecateBinding.inflate(LayoutInflater.from(context))
            binding.textView.text = "The GitLab version of Mitch will be deprecated next month, on March 1st. Please switch to F-Droid instead."

            setView(binding.root)
            setNegativeButton(android.R.string.cancel) { _, _ ->
                sharedPrefs.edit().run {
                    this.putBoolean(PREF_DONT_SHOW_DEPRECATION_DIALOG, binding.dontShowAgain.isChecked)
                    apply()
                }
            }
            setPositiveButton("Read more") { _, _ ->
                browseUrl("https://gardenapple.itch.io/mitch/devlog/217402/f-droid-release-and-paid-version")

                sharedPrefs.edit().run {
                    this.putBoolean(PREF_DONT_SHOW_DEPRECATION_DIALOG, binding.dontShowAgain.isChecked)
                    apply()
                }
            }
            create()
        }
        dialog.show()
    }

    override fun onBackPressed() {
        Log.d(LOGGING_TAG, "onBackPressed")
        if (browseFragment.isVisible) {
            val cantGoBack = browseFragment.onBackPressed()
            if (cantGoBack)
                finish()
            return
        }
        //super method handles fragment back stack
        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(LOGGING_TAG, "onSaveInstanceState: $currentFragmentTag")
        outState.putString(ACTIVE_FRAGMENT_KEY, currentFragmentTag)
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOGGING_TAG, "onResume: $currentFragmentTag")
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
     * @param itemId one of: R.id.navigation_website_view, R.id.navigation_settings, R.id.navigation_library
     * @param resetNavBar forcibly change the highlighted option in the bottom navigation bar
     * @return true if the current fragment has changed
     */
    fun setActiveFragment(newFragmentTag: String, resetNavBar: Boolean = true): Boolean {
        supportFragmentManager.beginTransaction().apply {
            if (newFragmentTag == currentFragmentTag)
                return false

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
        browseFragment.webView.loadUrl(url)
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
