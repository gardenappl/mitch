package garden.appl.mitch.ui

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.get
import garden.appl.mitch.*
import garden.appl.mitch.client.ItchTag
import garden.appl.mitch.client.ItchTagsParser
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.DatabaseCleanup
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.databinding.DialogTagSelectBinding
import garden.appl.mitch.install.Installations
import kotlinx.coroutines.*

class SettingsFragment : PreferenceFragmentCompat(), CoroutineScope by MainScope() {
    companion object {
        private const val LOGGING_TAG = "SettingsFrag"
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        updatePreferenceSummaries()
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

        } else if (preference.key == PREF_WEB_CACHE_ENABLE) {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            sharedPrefs.edit {
                putBoolean(PREF_WEB_CACHE_DIALOG_HIDE, true)
            }
            Log.d(LOGGING_TAG, "will now hide web cache dialog")

        } else if (preference.key == PREF_START_PAGE_EXCLUDE) {
            val binding = DialogTagSelectBinding.inflate(layoutInflater)
            val dialog = Dialog(requireContext()).apply {
                setTitle(R.string.settings_exclude_tags)
                setContentView(binding.root)
            }

            val loadTagsJob = launch {
                val tags = try {
                    ItchTagsParser.parseTags(ItchTag.Classification.GAME)
                } catch (e: Exception) {
                    binding.tagLoadingBar.visibility = View.GONE
                    binding.tagLoadingError.visibility = View.VISIBLE
                    Log.e(LOGGING_TAG, "Could not load tags", e)
                    return@launch
                }

                data class TagOption(val displayName: String, val tag: ItchTag?) {
                    override fun toString() = displayName
                }
                val options = mutableListOf(TagOption(
                    getString(R.string.settings_exclude_tags_show_all),
                    null
                ))
                options.addAll(tags.map { tag -> TagOption(tag.name, tag) })
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)

                binding.apply {
                    searchTags.addTextChangedListener { editable ->
                        adapter.filter.filter(editable)
                    }
                    tagLoadingBar.visibility = View.GONE
                    tagsList.visibility = View.VISIBLE
                    tagsList.adapter = adapter
                    tagsList.setOnItemClickListener { parent, view, position, id ->
                        val option = adapter.getItem(position)

                        val selectedTagDisplayString = if (option?.tag == null) {
                            getString(R.string.settings_exclude_tags_show_all)
                        } else {
                            option.tag.name
                        }

                        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                        sharedPrefs.edit(commit = true) {
                            putStringSet(PREF_START_PAGE_EXCLUDE, option?.tag?.let { setOf(it.tag) })
                            putString(PREF_START_PAGE_EXCLUDE_DISPLAY_STRING, selectedTagDisplayString)
                        }
                        this@SettingsFragment.updatePreferenceSummaries()
                        dialog.dismiss()
                    }
                }
            }

            dialog.setOnDismissListener { loadTagsJob.cancel() }
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

    private fun updatePreferenceSummaries() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val startPageExcludePreference = preferenceScreen.get<Preference>(PREF_START_PAGE_EXCLUDE)!!
        startPageExcludePreference.setSummary(sharedPrefs.getString(
            PREF_START_PAGE_EXCLUDE_DISPLAY_STRING,
            getString(R.string.settings_exclude_tags_show_all)
        ))
    }
}