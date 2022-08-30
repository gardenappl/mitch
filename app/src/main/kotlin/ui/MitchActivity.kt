package garden.appl.mitch.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import garden.appl.mitch.PREF_LANG_LOCALE

abstract class MitchActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(newBase)

        super.attachBaseContext(MitchContextWrapper.wrap(newBase,
            prefs.getString(PREF_LANG_LOCALE, "")!!))
    }
}