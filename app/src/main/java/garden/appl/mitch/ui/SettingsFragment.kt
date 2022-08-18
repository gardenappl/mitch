package garden.appl.mitch.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import garden.appl.mitch.Mitch
import garden.appl.mitch.R
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.DatabaseCleanup
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.install.Installations

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "cancel_all_downloads") {
            val dialog = AlertDialog.Builder(requireContext()).run {
                setTitle(R.string.settings_cancel_downloads_title)
                setMessage(R.string.settings_cancel_downloads_message)
                setIcon(R.drawable.ic_baseline_warning_24)

                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    runBlocking(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(requireContext())

                        val readyToInstall = ArrayList<Installation>()
                        for (install in db.installDao.getAllKnownInstallationsSync()) {
                            if (install.status == Installation.STATUS_INSTALLING ||
                                install.status == Installation.STATUS_READY_TO_INSTALL) {

                                Installations.cancelPending(context, install)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        //Re-attaching a fragment will redraw its UI
        //This takes care of changing day/night theme
        val manager = requireActivity().supportFragmentManager

        manager.beginTransaction().let {
            it.detach(this)
            it.commit()
        }
        manager.executePendingTransactions()
        manager.beginTransaction().let {
            it.attach(this)
            it.commit()
        }
    }
}