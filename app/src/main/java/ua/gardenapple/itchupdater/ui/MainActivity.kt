package ua.gardenapple.itchupdater.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.R



class MainActivity : AppCompatActivity() {

    private val browseFragment: BrowseFragment = BrowseFragment()
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
            add(R.id.fragmentContainer, settingsFragment, "settings")
            hide(settingsFragment)
            commit()
        }

        val navView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        navView.setOnNavigationItemSelectedListener { item ->
            Log.d(LOGGING_TAG, "hi when")
            when (item.itemId) {
                R.id.navigation_website_view -> {
                    Log.d(LOGGING_TAG, "view")
                    fragmentManager.beginTransaction().apply {
                        hide(activeFragment)
                        show(browseFragment)
                        commit()
                    }
                    activeFragment = browseFragment
                    true
                }

                R.id.navigation_settings -> {
                    Log.d(LOGGING_TAG, "settings")
                    fragmentManager.beginTransaction().apply {
                        hide(activeFragment)
                        show(settingsFragment)
                        commit()
                    }
                    activeFragment = settingsFragment
                    true
                }
                else -> false
            }
        }
    }
}
