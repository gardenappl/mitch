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
import android.widget.Toast
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class MitchActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {

    private var notificationRequestDialog: AlertDialog? = null
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
                    Mitch.externalFileManager.resumeMoveToDownloads(this)
            PERMISSION_REQUEST_START_DOWNLOAD ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeRequestPermission()
            PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT ->
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    Mitch.externalFileManager.resumeGetViewIntent(this)
            PERMISSION_REQUEST_NOTIFICATION -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    try {
                        NotificationManagerCompat.from(this)
                            .notify(pendingNotifications.map { entry ->
                                NotificationWithIdAndTag(entry.key.first, entry.key.second, entry.value)
                            })
                        pendingNotifications.clear()
                    } catch (e: SecurityException) {
                        Log.w(LOGGING_TAG, "Could not send notif", e)
                        throw e
                    }
                } else if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    notificationCancelMessage?.let {
                        Toast.makeText(this, it, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    fun requestNotificationPermission(scope: CoroutineScope,
                                      @StringRes explanationMessage: Int,
                                      @StringRes cancelMessage: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || notificationRequestDialog?.isShowing == true)
            return
        if (prefs.getBoolean(PREF_NO_NOTIFICATIONS, false)) {
            scope.launch(Dispatchers.Main) {
                Toast.makeText(this@MitchActivity, cancelMessage, Toast.LENGTH_LONG).show()
            }
            return
        }
        val shouldShowRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
        Log.d(LOGGING_TAG, "Should show rationale: $shouldShowRationale")

        if (shouldShowRationale) {
            scope.launch(Dispatchers.Main) {
                val dialog = AlertDialog.Builder(this@MitchActivity).run {
                    setTitle(R.string.dialog_notification_explain_title)
                    setMessage(explanationMessage)
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
                        Toast.makeText(context, cancelMessage, Toast.LENGTH_LONG).show()
                    }
                    setOnDismissListener {
                        Log.d(LOGGING_TAG, "dismissed!")
                        this@MitchActivity.notificationRequestDialog = null
                    }
                    create()
                }
                notificationRequestDialog = dialog
                Log.d(LOGGING_TAG, "showing!")
                dialog.show()
            }
        } else {
            ActivityCompat.requestPermissions(
                this@MitchActivity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_NOTIFICATION
            )
        }
    }

    companion object {
        private const val LOGGING_TAG = "MitchActivity"

        private val pendingNotifications = HashMap<Pair<String, Int>, Notification>()
        @StringRes
        private var notificationCancelMessage: Int? = null

        fun tryNotifyWithPermission(
            mitchActivity: MitchActivity?, context: Context, scope: CoroutineScope?,
            tag: String, id: Int, notification: Notification,
            @StringRes explanationMessage: Int, @StringRes cancelMessage: Int
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                    with(NotificationManagerCompat.from(context)) {
                        notify(tag, id, notification)
                    }
                } else if (mitchActivity != null && scope != null) {
                    mitchActivity.requestNotificationPermission(scope, explanationMessage, cancelMessage)
                    pendingNotifications[Pair(tag, id)] = notification
                    notificationCancelMessage = cancelMessage
                } else {
                    Log.w(LOGGING_TAG, "Did not deliver notification! ${context.getString(explanationMessage)}")
                    pendingNotifications[Pair(tag, id)] = notification
                    notificationCancelMessage = cancelMessage
                }
            } else {
                with(NotificationManagerCompat.from(context)) {
                    notify(tag, id, notification)
                }
            }
        }
    }
}