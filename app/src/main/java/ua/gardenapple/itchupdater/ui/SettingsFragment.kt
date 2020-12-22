package ua.gardenapple.itchupdater.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import ua.gardenapple.itchupdater.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

//        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
//        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

//        if (currentNightMode != newNightMode) {
            //Re-attaching a fragment will redraw its UI

            (activity as MainActivity).supportFragmentManager.beginTransaction().apply {
                detach(this@SettingsFragment)
                attach(this@SettingsFragment)
                commit()
            }
//        }
    }
}