package garden.appl.mitch.ui

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import garden.appl.mitch.Mitch
import garden.appl.mitch.PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT
import garden.appl.mitch.PERMISSION_REQUEST_MOVE_TO_DOWNLOADS
import garden.appl.mitch.PERMISSION_REQUEST_NOTIFICATION
import garden.appl.mitch.PERMISSION_REQUEST_START_DOWNLOAD
import garden.appl.mitch.PREF_LANG_LOCALE
import garden.appl.mitch.PREF_LANG_LOCALE_NEXT
import garden.appl.mitch.PREF_NO_NOTIFICATIONS
import garden.appl.mitch.R

abstract class MitchActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_MOVE_TO_DOWNLOADS ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeMoveToDownloads()
            PERMISSION_REQUEST_START_DOWNLOAD ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeRequestPermission()
            PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeGetViewIntent(this)
            PERMISSION_REQUEST_NOTIFICATION -> {
                try {
                    NotificationManagerCompat.from(this)
                        .notify(pendingNotifications.map { entry ->
                            NotificationWithIdAndTag(entry.key.first, entry.key.second, entry.value)
                        })
                    pendingNotifications.clear()
                } catch (e: SecurityException) {
                    throw e
                }
            }
        }
    }

    fun requestNotificationPermission() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
            && !prefs.getBoolean(PREF_NO_NOTIFICATIONS, false)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            AlertDialog.Builder(this).run {
                setTitle(R.string.dialog_notification_explain_title)
                setMessage(R.string.dialog_notification_explain_download)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this@MitchActivity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_NOTIFICATION
                    )
                }
                setNegativeButton(android.R.string.cancel) { _, _ ->
                    prefs.edit {
                        putBoolean(PREF_NO_NOTIFICATIONS, true)
                    }
                }
                show()
            }
        }
    }

    companion object {
        private val pendingNotifications = HashMap<Pair<String, Int>, Notification>()

        fun tryNotifyWithPermission(
            mitchActivity: MitchActivity?, context: Context,
            tag: String, id: Int, notification: Notification,
            @StringRes explanationDialog: Int
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                    with(NotificationManagerCompat.from(context)) {
                        notify(tag, id, notification)
                    }
                } else if (mitchActivity != null) {
                    mitchActivity.requestNotificationPermission()
                    pendingNotifications[Pair(tag, id)] = notification
                } else {
                    Log.w(Mitch.LOGGING_TAG, "Did not deliver notification! ${context.getString(explanationDialog)}")
                    pendingNotifications[Pair(tag, id)] = notification
                }
            } else {
                with(NotificationManagerCompat.from(context)) {
                    notify(tag, id, notification)
                }
            }
        }
    }
}