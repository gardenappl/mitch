package ua.gardenapple.itchupdater.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.installer.DownloadRequester


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    var browseFragment: BrowseFragment = BrowseFragment()
        private set
    var libraryFragment: LibraryFragment = LibraryFragment()
        private set
    var settingsFragment: SettingsFragment = SettingsFragment()
        private set

    var activeFragment: Fragment = browseFragment
        private set

    companion object {
        private const val SELECTED_FRAGMENT_KEY: String = "fragment"

        private const val BROWSE_FRAGMENT_TAG: String = "browse"
        private const val LIBRARY_FRAGMENT_TAG: String = "library"
        private const val SETTINGS_FRAGMENT_TAG: String = "settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        //Add app bar, hidden by default
        setSupportActionBar(toolbar)
        supportActionBar!!.hide()


        val activeFragmentTag = savedInstanceState?.getString(SELECTED_FRAGMENT_KEY) ?: BROWSE_FRAGMENT_TAG

        //Fragments aren't destroyed on configuration changes
        val tryBrowseFragment = supportFragmentManager.findFragmentByTag(BROWSE_FRAGMENT_TAG)

        if (tryBrowseFragment != null) {
            browseFragment = tryBrowseFragment as BrowseFragment
            libraryFragment = supportFragmentManager.findFragmentByTag(LIBRARY_FRAGMENT_TAG) as LibraryFragment
            settingsFragment = supportFragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG) as SettingsFragment

        } else {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, browseFragment, BROWSE_FRAGMENT_TAG)
                if(activeFragmentTag != BROWSE_FRAGMENT_TAG)
                    hide(browseFragment)
                commit()
            }
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, libraryFragment, LIBRARY_FRAGMENT_TAG)
                if(activeFragmentTag != LIBRARY_FRAGMENT_TAG)
                    hide(libraryFragment)
                commit()
            }
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragmentContainer, settingsFragment, SETTINGS_FRAGMENT_TAG)
                if(activeFragmentTag != SETTINGS_FRAGMENT_TAG)
                    hide(settingsFragment)
                commit()
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (!settingsFragment.isHidden)
                activeFragment = settingsFragment
            else if (!libraryFragment.isHidden)
                activeFragment = libraryFragment
            else
                activeFragment = browseFragment

            onFragmentSwitch(getItemId(activeFragment), true)
        }

        val navView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        navView.setOnNavigationItemSelectedListener { item ->
            val fragmentChanged = switchToFragment(item.itemId, false)

            if (!fragmentChanged && activeFragment == browseFragment)
                browseFragment.webView.loadUrl(ItchWebsiteUtils.getMainBrowsePage())

            return@setOnNavigationItemSelectedListener fragmentChanged
        }

        switchToFragment(getItemId(activeFragmentTag), true)
    }

    override fun onStart() {
        super.onStart()

        if (intent.action == Intent.ACTION_VIEW &&
                intent.data?.let { ItchWebsiteUtils.isItchWebPage(it) } == true) {
            switchToFragment(R.id.navigation_website_view)
            browseFragment.webView.loadUrl(intent.data!!.toString())
        }
    }

    override fun onBackPressed() {
        if (activeFragment === browseFragment) {
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
        outState.putString(SELECTED_FRAGMENT_KEY, getFragmentTag(activeFragment))
    }

    // Handle light/dark theme changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

        Log.d(LOGGING_TAG, "dark equal? " + (R.color.colorBackground == R.color.colorPrimaryDark))
        Log.d(LOGGING_TAG, "light equal? " + (R.color.colorBackground == R.color.colorPrimary))

        val backgroundColor = Utils.getColor(resources, R.color.colorBackground, theme)
        val foregroundColor = Utils.getColor(resources, R.color.colorForeground, theme)
        val accentColor = Utils.getColor(resources, R.color.colorAccent, theme)

        val itemColorStateList = Utils.colorStateListOf(
            intArrayOf(android.R.attr.state_selected) to accentColor,
            intArrayOf() to foregroundColor
        )

        bottomNavigationView.setBackgroundColor(backgroundColor)
        bottomNavigationView.itemBackground = ColorDrawable(backgroundColor)
        bottomNavigationView.itemIconTintList = itemColorStateList
        bottomNavigationView.itemTextColor = itemColorStateList
        mainLayout.setBackgroundColor(backgroundColor)
        
        if (activeFragment == browseFragment) {
            // Browse fragment has special handling for system bar color
            browseFragment.updateUI()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = backgroundColor
                if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_NO) {
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
    fun switchToFragment(itemId: Int, resetNavBar: Boolean = true): Boolean {
        val newFragment = getFragment(itemId)

        if (newFragment === activeFragment)
            return false

        supportFragmentManager.beginTransaction().apply {
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            hide(activeFragment)
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            show(newFragment)

            addToBackStack(null)

            commit()
        }

        onFragmentSwitch(itemId, resetNavBar)

        return true
    }

    private fun onFragmentSwitch(itemId: Int, resetNavBar: Boolean) {
        val newFragment = getFragment(itemId)

        activeFragment = newFragment

        if (resetNavBar)
            navBarSelectItem(itemId)

        if (newFragment == browseFragment) {
            browseFragment.updateUI()
            speedDial.show()
        } else {
            speedDial.hide()
        }
    }

    private fun navBarSelectItem(itemId: Int) {
        bottomNavigationView.post {
            var i = 0
            val menu = bottomNavigationView.menu
            while (i < menu.size()) {
                val item = bottomNavigationView.menu.getItem(i)
                if (item.itemId == itemId)
                    item.isChecked = true
                i++
            }
        }
    }


    private fun getFragmentTag(fragment: Fragment): String {
        return when (fragment) {
            browseFragment -> BROWSE_FRAGMENT_TAG
            libraryFragment -> LIBRARY_FRAGMENT_TAG
            settingsFragment -> SETTINGS_FRAGMENT_TAG
            else -> throw IllegalArgumentException()
        }
    }

    private fun getFragment(tag: String): Fragment {
        return when (tag) {
            BROWSE_FRAGMENT_TAG -> browseFragment
            LIBRARY_FRAGMENT_TAG -> libraryFragment
            SETTINGS_FRAGMENT_TAG -> settingsFragment
            else -> throw IllegalArgumentException()
        }
    }

    private fun getFragment(itemId: Int): Fragment {
        return when (itemId) {
            R.id.navigation_website_view -> browseFragment
            R.id.navigation_library -> libraryFragment
            R.id.navigation_settings -> settingsFragment
            else -> throw IllegalArgumentException()
        }
    }

    private fun getItemId(tag: String): Int {
        return when(tag) {
            BROWSE_FRAGMENT_TAG -> R.id.navigation_website_view
            LIBRARY_FRAGMENT_TAG -> R.id.navigation_library
            SETTINGS_FRAGMENT_TAG -> R.id.navigation_settings
            else -> throw IllegalArgumentException()
        }
    }

    private fun getItemId(fragment: Fragment): Int {
        return when(fragment) {
            browseFragment -> R.id.navigation_website_view
            libraryFragment -> R.id.navigation_library
            settingsFragment -> R.id.navigation_settings
            else -> throw IllegalArgumentException()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        super.onOptionsItemSelected(item)
        return false
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE_DOWNLOAD -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    DownloadRequester.resumeDownload(getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                }
            }
        }
    }
}
