package ua.gardenapple.itchupdater.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.URLUtil
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.browse_fragment.*
import ua.gardenapple.itchupdater.*
import java.net.URLConnection


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var browseFragment: BrowseFragment
    private lateinit var currentFragmentTag: String

    companion object {
        const val EXTRA_SHOULD_OPEN_LIBRARY = "SHOULD_OPEN_LIBRARY"
        
        private const val ACTIVE_FRAGMENT_KEY: String = "fragment"

        const val BROWSE_FRAGMENT_TAG: String = "browse"
        const val LIBRARY_FRAGMENT_TAG: String = "library"
        const val SETTINGS_FRAGMENT_TAG: String = "settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Initially set to SplashScreenTheme during loading, this sets the proper theme
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Add app bar, hidden by default
        setSupportActionBar(toolbar)
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
            setActiveFragment(BROWSE_FRAGMENT_TAG)
            browseFragment.webView.loadUrl(intent.data!!.toString())
        } else if (intent.getBooleanExtra(EXTRA_SHOULD_OPEN_LIBRARY, false)) {
            setActiveFragment(LIBRARY_FRAGMENT_TAG)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ACTIVE_FRAGMENT_KEY, currentFragmentTag)
    }

    // Handle light/dark theme changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val backgroundColor = Utils.getColor(resources, R.color.colorBackground, theme)
        val backgroundMainColor = Utils.getColor(resources, R.color.colorBackgroundMain, theme)
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
        mainLayout.setBackgroundColor(backgroundMainColor)

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

    private fun onFragmentSet(newFragmentTag: String, resetNavBar: Boolean) {
        if (resetNavBar)
            navBarSelectItem(getItemId(newFragmentTag))


        if (currentFragmentTag == BROWSE_FRAGMENT_TAG && newFragmentTag != BROWSE_FRAGMENT_TAG)
            browseFragment.restoreDefaultUI()

        currentFragmentTag = newFragmentTag

        if (newFragmentTag == BROWSE_FRAGMENT_TAG) {
            browseFragment.updateUI()
            speedDial.show()
        } else {
            speedDial.hide()
        }
    }

    private fun navBarSelectItem(itemId: Int) {
        bottomNavigationView.post {
            val menu = bottomNavigationView.menu
            
            for (index in 0 until menu.size()) {
                val item = bottomNavigationView.menu.getItem(index)
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
            SETTINGS_FRAGMENT_TAG -> R.id.navigation_settings
            else -> throw IllegalArgumentException()
        }
    }
    
    private fun getFragmentTag(itemId: Int): String {
        return when (itemId) {
            R.id.navigation_website_view -> BROWSE_FRAGMENT_TAG
            R.id.navigation_library -> LIBRARY_FRAGMENT_TAG
            R.id.navigation_settings -> SETTINGS_FRAGMENT_TAG
            else -> throw IllegalArgumentException()
        }
    }
    
    private fun getFragmentClass(tag: String): Class<out Fragment> {
        return when (tag) {
            BROWSE_FRAGMENT_TAG -> BrowseFragment::class.java
            LIBRARY_FRAGMENT_TAG -> LibraryFragment::class.java
            SETTINGS_FRAGMENT_TAG -> SettingsFragment::class.java
            else -> throw IllegalArgumentException()
        }
    }

    //TODO: handle permissions
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        when (requestCode) {
//            PERMISSION_REQUEST_CODE_DOWNLOAD -> {
//                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    DownloadFileManager.resumeDownload(getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
//                }
//            }
//        }
//    }
}
