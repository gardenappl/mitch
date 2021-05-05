package ua.gardenapple.itchupdater.ui

import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.NOTIFICATION_CHANNEL_ID_INSTALLING
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.DatabaseCleanup
import ua.gardenapple.itchupdater.database.installation.Installation

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        //Re-attaching a fragment will redraw its UI
        //This takes care of changing day/night theme
        (activity as MainActivity).supportFragmentManager.beginTransaction().apply {
            detach(this@SettingsFragment)
            attach(this@SettingsFragment)
            commit()
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference?.key == "cancel_all_downloads") {
            val dialog = AlertDialog.Builder(requireContext()).run {
                setTitle(R.string.settings_cancel_downloads_title)
                setMessage(R.string.settings_cancel_downloads_message)
                setIcon(R.drawable.ic_baseline_warning_24)

                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    runBlocking(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(requireContext())

                        val readyToInstall = ArrayList<Installation>()
                        for (install in db.installDao.getAllKnownInstallationsSync()) {
                            if (install.status == Installation.STATUS_INSTALLING) {
                                requireContext().packageManager.packageInstaller
                                    .abandonSession(install.downloadOrInstallId!!.toInt())
                            } else if (install.status == Installation.STATUS_READY_TO_INSTALL) {
                                readyToInstall.add(install)
                            }
                        }
                        db.installDao.delete(readyToInstall)

                        Mitch.fileManager.deleteAllDownloads(context)

                        DatabaseCleanup(requireContext()).cleanAppDatabase(db)
                    }
                }
                setNegativeButton(R.string.dialog_no) { _, _ -> /* No-op */ }

                create()
            }

            dialog.show()
            return true
        }
        return false
    }
}