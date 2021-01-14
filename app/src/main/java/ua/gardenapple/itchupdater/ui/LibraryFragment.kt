package ua.gardenapple.itchupdater.ui

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.FixedPreloadSizeProvider
import kotlinx.coroutines.*
import org.ocpsoft.prettytime.PrettyTime
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.PREF_LAST_UPDATE_CHECK
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.UPDATE_CHECK_TASK_TAG
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.UpdateCheckWorker
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.*
import ua.gardenapple.itchupdater.database.installation.GameInstallation
import ua.gardenapple.itchupdater.databinding.LibraryFragmentBinding
import ua.gardenapple.itchupdater.gitlab.GitlabUpdateCheckWorker
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

class LibraryFragment : Fragment(), CoroutineScope by MainScope() {
    private var _binding: LibraryFragmentBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    private lateinit var pendingViewModel: PendingGameViewModel
    private lateinit var installedViewModel: InstalledGameViewModel
    private lateinit var downloadsViewModel: GameDownloadsViewModel

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PREF_LAST_UPDATE_CHECK) {
                launch {
                    setUpdateCheckInfo(prefs)
                }
            }
        }

    companion object {
        //2x of 315x250
        const val THUMBNAIL_WIDTH = 630
        const val THUMBNAIL_HEIGHT = 500
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = LibraryFragmentBinding.inflate(inflater, container, false)
        val view = binding.root


        val pendingList = view.findViewById<RecyclerView>(R.id.pending_list)
        val pendingLabel = view.findViewById<TextView>(R.id.pending_label)
        val pendingAdapter = GameListAdapter(requireActivity(), pendingList, GameRepository.Type.Pending)
        pendingList.adapter = pendingAdapter
        pendingList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        var sizeProvider = FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        var modelProvider = LibraryPreloadModelProvider(pendingAdapter)
        var preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        pendingList.addOnScrollListener(preloader)

        pendingViewModel = ViewModelProvider(this).get(PendingGameViewModel::class.java)
        pendingViewModel.pendingGames.observe(viewLifecycleOwner, { gameInstalls ->
            gameInstalls?.let {
                pendingAdapter.gameInstalls = gameInstalls
            }
            view.post {
                if (gameInstalls?.isNotEmpty() == true) {
                    pendingList.visibility = View.VISIBLE
                    pendingLabel.visibility = View.VISIBLE
                } else {
                    pendingList.visibility = View.GONE
                    pendingLabel.visibility = View.GONE
                }
            }
        })
        


        val downloadsList = view.findViewById<RecyclerView>(R.id.downloads_list)
        val downloadsAdapter = GameListAdapter(requireActivity(), downloadsList, GameRepository.Type.Downloads)
        downloadsList.adapter = downloadsAdapter
        downloadsList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        sizeProvider = FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        modelProvider = LibraryPreloadModelProvider(downloadsAdapter)
        preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        downloadsList.addOnScrollListener(preloader)

        downloadsViewModel = ViewModelProvider(this).get(GameDownloadsViewModel::class.java)
        downloadsViewModel.gameDownloads.observe(viewLifecycleOwner, { gameInstalls ->
            gameInstalls?.let { downloadsAdapter.gameInstalls = gameInstalls }
        })



        val installedList = view.findViewById<RecyclerView>(R.id.installed_list)
        val installedAdapter = GameListAdapter(requireActivity(), installedList, GameRepository.Type.Installed)
        installedList.adapter = installedAdapter
        installedList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        sizeProvider = FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        modelProvider = LibraryPreloadModelProvider(installedAdapter)
        preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        installedList.addOnScrollListener(preloader)

        installedViewModel = ViewModelProvider(this).get(InstalledGameViewModel::class.java)
        installedViewModel.installedGames.observe(viewLifecycleOwner, Observer { gameInstalls ->
            gameInstalls?.let { installedAdapter.gameInstalls = gameInstalls }
        })

        //TODO: Go to actual UpdateCheckActivity
        binding.goToUpdateCheck.setOnClickListener { _ ->
            Toast.makeText(requireContext(), "Update check started", Toast.LENGTH_LONG)
                .show()
            WorkManager.getInstance(requireContext())
                .enqueue(OneTimeWorkRequest.from(UpdateCheckWorker::class.java))
            WorkManager.getInstance(requireContext())
                .enqueue(OneTimeWorkRequest.from(GitlabUpdateCheckWorker::class.java))
        }
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        launch {
            setUpdateCheckInfo(sharedPrefs)
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        return view
    }

    private inner class LibraryPreloadModelProvider(
        val adapter: GameListAdapter
    ) : ListPreloader.PreloadModelProvider<GameInstallation> {
        override fun getPreloadItems(position: Int): MutableList<GameInstallation> {
            if(adapter.gameInstalls.isEmpty())
                return Collections.emptyList()
            else
                return Collections.singletonList(adapter.gameInstalls[position])
        }

        override fun getPreloadRequestBuilder(item: GameInstallation): RequestBuilder<*> {
            return Glide.with(this@LibraryFragment)
                .load(item.game.thumbnailUrl)
                .override(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        }
    }

    private suspend fun setUpdateCheckInfo(sharedPrefs: SharedPreferences) {
        val results = withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.updateCheckDao.getNotUpToDateResultsSync()
        }
        
        _binding?.let { binding ->

            if (results.count { result -> result.code == UpdateCheckResult.ERROR } > 0)
                binding.updateCheckInfo.setText(R.string.library_error)
            else if (results.isNotEmpty())
                binding.updateCheckInfo.text = requireContext().resources.getQuantityString(
                    R.plurals.library_updates_available,
                    results.size,
                    results.size
                )
            else
                binding.updateCheckInfo.setText(R.string.library_all_up_to_date)


            val timestamp = sharedPrefs.getLong(PREF_LAST_UPDATE_CHECK, 0)
            if (timestamp > 0) {
                val instant = Instant.ofEpochMilli(timestamp)
                binding.updateCheckTimestamp.text = requireContext().resources.getString(
                    R.string.library_last_update_check_time,
                    Mitch.prettyTime.format(instant)
                )
            } else {
                binding.updateCheckTimestamp.text = ""
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        //Re-attaching a fragment will redraw its UI
        //This takes care of changing day/night theme
        (activity as MainActivity).supportFragmentManager.beginTransaction().apply {
            detach(this@LibraryFragment)
            attach(this@LibraryFragment)
            commit()
        }
    }
}