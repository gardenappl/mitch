package garden.appl.mitch.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import garden.appl.mitch.*
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.databinding.ActivityMainBinding

/**
 * The [MainActivity] handles a lot of things, including day/night themes and languages
 */
class MainActivity : MitchActivity(), CoroutineScope by MainScope() {

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
            else {
                Log.w(LOGGING_TAG, "no visible fragment?")
                return@addOnBackStackChangedListener
            }
            onFragmentSet(newFragmentTag, true)
        }

        val navView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        navView.setOnNavigationItemSelectedListener { item ->
            val fragmentChanged = setActiveFragment(getFragmentTag(item.itemId), false)

            if (!fragmentChanged && currentFragmentTag == BROWSE_FRAGMENT_TAG)
                browseFragment.loadUrl(ItchWebsiteUtils.getMainBrowsePage(this))

            return@setOnNavigationItemSelectedListener fragmentChanged
        }
    }

    override fun onStart() {
        super.onStart()

        if (intent.action == Intent.ACTION_VIEW &&
                intent.data?.let { ItchWebsiteUtils.isItchWebPage(it) } == true) {
            browseUrl(intent.data.toString())
        } else if (intent.getBooleanExtra(EXTRA_SHOULD_OPEN_LIBRARY, false)) {
            setActiveFragment(LIBRARY_FRAGMENT_TAG)
        } else {
            setActiveFragment(currentFragmentTag)
        }

        launch {
            // Force lazy-init database to fully initialize, in the background
            val db = AppDatabase.getDatabase(this@MainActivity)
            if (!db.isOpen)
                db.installDao.getInstallationByPackageName(packageName)
        }
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
        Log.d(LOGGING_TAG, "current: $currentFragmentTag, new: $newFragmentTag")
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


    override fun makeIntentForRestart(): Intent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(browseFragment.url),
            applicationContext,
            MainActivity::class.java
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Redirect for <application> "manageSpaceActivity" attribute
     */
    class LibraryActivity : AppCompatActivity() {
        override fun onStart() {
            super.onStart()
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(EXTRA_SHOULD_OPEN_LIBRARY, true)
            startActivity(intent)
            finish()
        }
    }
}
