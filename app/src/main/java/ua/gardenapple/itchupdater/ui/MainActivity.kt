package ua.gardenapple.itchupdater.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import ua.gardenapple.itchupdater.BuildConfig
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.R



class MainActivity : AppCompatActivity() {

    private val browseFragment: BrowseFragment = BrowseFragment()
    private val libraryFragment: LibraryFragment = LibraryFragment()
    private val settingsFragment: SettingsFragment = SettingsFragment()

    private var activeFragment: Fragment = browseFragment
    private val fragmentManager: FragmentManager = supportFragmentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, browseFragment, "browse")
            commit()
        }
        fragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, libraryFragment, "library")
            hide(libraryFragment)
            commit()
        }
        fragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, settingsFragment, "settings")
            hide(settingsFragment)
            commit()
        }

        val navView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        navView.setOnNavigationItemSelectedListener { item ->
            switchToFragment(item.itemId, false)
        }
    }

    /**
     * @param itemId one of: R.id.navigation_website_view, R.id.navigation_settings, R.id.navigation_library
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

        if(newFragment === activeFragment)
            return false

        fragmentManager.beginTransaction().apply {
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            hide(activeFragment)
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            show(newFragment)
            commit()
        }
        activeFragment = newFragment

        if(resetNavBar)
            findViewById<BottomNavigationView>(R.id.bottomNavigationView).selectedItemId = itemId

        return true
    }

    override fun onStart() {
        super.onStart()

        Log.d(LOGGING_TAG, "Starting...")
        Log.d(LOGGING_TAG, "Version: ${BuildConfig.VERSION_NAME}")
        Log.d(LOGGING_TAG, "Action: ${intent.action}")
        Log.d(LOGGING_TAG, "Data: ${intent.data}")
        if(intent.action == Intent.ACTION_VIEW && intent.data?.let { ItchWebsiteUtils.isItchWebPage(it) } == true) {
            switchToFragment(R.id.navigation_website_view)
            browseFragment.getWebView().loadUrl(intent.data!!.toString())
        }
    }

    override fun onBackPressed() {
        if(activeFragment === browseFragment) {
            val cantGoBack = browseFragment.onBackPressed()
            if (cantGoBack)
                super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }
}
