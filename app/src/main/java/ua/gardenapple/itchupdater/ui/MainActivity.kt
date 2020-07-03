package ua.gardenapple.itchupdater.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
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
        const val SELECTED_FRAGMENT_KEY: String = "fragment"

        const val BROWSE_FRAGMENT_TAG: String = "browse"
        const val LIBRARY_FRAGMENT_TAG: String = "library"
        const val SETTINGS_FRAGMENT_TAG: String = "settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        //Add app bar, hidden by default
        setSupportActionBar(toolbar)
        supportActionBar!!.hide()


        Log.d(LOGGING_TAG, "Stored active fragment tag: ${savedInstanceState?.getString(
            SELECTED_FRAGMENT_KEY)}")
        val activeFragmentTag = savedInstanceState?.getString(SELECTED_FRAGMENT_KEY) ?: BROWSE_FRAGMENT_TAG

        Log.d(LOGGING_TAG, "Active fragment tag: $activeFragmentTag")

        //Fragments aren't destroyed on configuration changes
        val tryBrowseFragment = supportFragmentManager.findFragmentByTag(BROWSE_FRAGMENT_TAG)

        if (tryBrowseFragment != null) {
            Log.d(LOGGING_TAG, "Fragment Manager contains some fragments")
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

        activeFragment = getFragment(activeFragmentTag)!!

        Log.d(LOGGING_TAG, "Fragment manager contains ${supportFragmentManager.fragments.size} fragments")

        val navView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        navView.setOnNavigationItemSelectedListener { item ->
            Log.d(LOGGING_TAG, "Current selected: ${bottomNavigationView.selectedItemId}")
            Log.d(LOGGING_TAG, "New: ${item.itemId}")
            val fragmentChanged = switchToFragment(item.itemId, false)

            if (!fragmentChanged && activeFragment == browseFragment)
                browseFragment.webView.loadUrl(ItchWebsiteUtils.getMainBrowsePage())

            return@setOnNavigationItemSelectedListener fragmentChanged
        }
    }

    /**
     * @param itemId one of: R.id.navigation_website_view, R.id.navigation_settings, R.id.navigation_library
     * @param resetNavBar forcibly change the highlighted option in the bottom navigation bar
     * @return true if the current fragment has changed
     */
    fun switchToFragment(itemId: Int, resetNavBar: Boolean = true): Boolean {
        val newFragment: Fragment

        when (itemId) {
            R.id.navigation_website_view -> newFragment = browseFragment
            R.id.navigation_library -> newFragment = libraryFragment
            R.id.navigation_settings -> newFragment = settingsFragment
            else -> return false
        }

        if (newFragment === activeFragment)
            return false

        supportFragmentManager.beginTransaction().apply {
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            hide(activeFragment)
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            show(newFragment)
            commit()
        }
        activeFragment = newFragment

        if (resetNavBar) {
            bottomNavigationView.post {
                var i = 0
                val menu = bottomNavigationView.menu
                while (i < menu.size()) {
                    val item = bottomNavigationView.menu.getItem(i)
                    if (item.itemId == itemId)
                        item.isChecked = true
                    i++
                }
               /* bottomNavigationView.selectedItemId = itemId
                bottomNavigationViewSetIdHack(itemId)*/
            }
        }

        if (newFragment == browseFragment) {
            browseFragment.updateUI()
            speedDial.show()
        } else
            speedDial.hide()

        return true
    }

    override fun onStart() {
        super.onStart()

        Log.d(LOGGING_TAG, "Starting...")
        Log.d(LOGGING_TAG, "Version: ${BuildConfig.VERSION_NAME}")
        Log.d(LOGGING_TAG, "Action: ${intent.action}")
        Log.d(LOGGING_TAG, "Data: ${intent.data}")
        if (intent.action == Intent.ACTION_VIEW &&
                intent.data?.let { ItchWebsiteUtils.isItchWebPage(it) } == true) {
            switchToFragment(R.id.navigation_website_view)
            browseFragment.webView.loadUrl(intent.data!!.toString())
        }
    }

    override fun onBackPressed() {
        if(activeFragment === browseFragment) {
            val cantGoBack = browseFragment.onBackPressed()
            if (!cantGoBack) {
                return
            }
        }
        //TODO: handle fragments back stack
        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SELECTED_FRAGMENT_KEY, getFragmentTag(activeFragment))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
//        if(activeFragment !== browseFragment) {
//            supportFragmentManager.beginTransaction().apply {
//                detach(activeFragment)
//                attach(activeFragment)
//                commit()
//            }
//        }
    }


    fun getFragmentTag(fragment: Fragment): String? {
        when(fragment) {
            browseFragment -> return BROWSE_FRAGMENT_TAG
            libraryFragment -> return LIBRARY_FRAGMENT_TAG
            settingsFragment -> return SETTINGS_FRAGMENT_TAG
            else -> return null
        }
    }

    fun getFragment(tag: String): Fragment? {
        when(tag) {
            BROWSE_FRAGMENT_TAG -> return browseFragment
            LIBRARY_FRAGMENT_TAG -> return libraryFragment
            SETTINGS_FRAGMENT_TAG -> return settingsFragment
            else -> return null
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
