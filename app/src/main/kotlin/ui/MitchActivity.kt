package garden.appl.mitch.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import garden.appl.mitch.PREF_LANG_LOCALE
import garden.appl.mitch.PREF_LANG_LOCALE_NEXT
import garden.appl.mitch.R

abstract class MitchActivity : AppCompatActivity() {
    private val langChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener langChange@{ prefs, key ->
            if (key != PREF_LANG_LOCALE_NEXT)
                return@langChange

            val currentLocale = prefs.getString(PREF_LANG_LOCALE, "null?")
            val nextLocale = prefs.getString(PREF_LANG_LOCALE_NEXT, "null???")
//            Log.d(MainActivity.LOGGING_TAG, "handling locale change to $nextLocale, currently $currentLocale")
            if (nextLocale == currentLocale)
                return@langChange

            AlertDialog.Builder(this).run {
                setTitle(R.string.dialog_lang_restart_title)
                setMessage(R.string.dialog_lang_restart)

                setPositiveButton(android.R.string.ok) { _, _ ->
                    this@MitchActivity.finish()
                    //Use 'post' method to make sure that Activity lifecycle events
                    //run before the process exits
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.post {
                        context.startActivity(makeIntentForRestart())
                        Runtime.getRuntime().exit(0)
                    }
                }
                setNegativeButton(android.R.string.cancel) { _, _ -> /* No-op */ }

                show()
            }
        }

    /**
     * Will be used to restart the activity on language change.
     * Not super sure about this last bit but I think it might be better to use
     * [getApplicationContext] instead of `this` when creating an [Intent] for this.
     */
    abstract fun makeIntentForRestart(): Intent

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(langChangeListener)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(langChangeListener)
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(newBase)

        super.attachBaseContext(MitchContextWrapper.wrap(newBase,
            prefs.getString(PREF_LANG_LOCALE, "")!!))
    }
}