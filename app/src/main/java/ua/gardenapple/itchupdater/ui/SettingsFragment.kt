package ua.gardenapple.itchupdater.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import ua.gardenapple.itchupdater.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}