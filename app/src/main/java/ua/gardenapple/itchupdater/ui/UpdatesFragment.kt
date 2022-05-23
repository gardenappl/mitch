package ua.gardenapple.itchupdater.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.FixedPreloadSizeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.client.UpdateChecker
import ua.gardenapple.itchupdater.database.updatecheck.InstallUpdateCheckResult
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultViewModel
import ua.gardenapple.itchupdater.databinding.UpdatesFragmentBinding
import java.util.*


class UpdatesFragment : Fragment(), CoroutineScope by MainScope() {
    private var _binding: UpdatesFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var availableResultsViewModel: UpdateCheckResultViewModel

    companion object {
        const val THUMBNAIL_WIDTH = 315
        const val THUMBNAIL_HEIGHT = 250
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = UpdatesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = UpdatesListAdapter(requireActivity(), binding.updateResults)
        binding.updateResults.adapter = adapter
        binding.updateResults.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        //Glide thumbnail handling
        val sizeProvider = FixedPreloadSizeProvider<InstallUpdateCheckResult>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        val modelProvider = UpdatesPreloadModelProvider(adapter)
        val preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        binding.updateResults.addOnScrollListener(preloader)

        availableResultsViewModel = ViewModelProvider(this).get(UpdateCheckResultViewModel::class.java)
        availableResultsViewModel.availableUpdates.observe(viewLifecycleOwner, { availableUpdates ->
            availableUpdates?.let {
                adapter.availableUpdates = availableUpdates
            }
            view.post {
                _binding?.let { binding ->
                    if (availableUpdates?.isNotEmpty() == true) {
                        binding.updateResults.visibility = View.VISIBLE
                        binding.allUpToDate.visibility = View.GONE
                        binding.pullToRefresh.visibility = View.GONE
                    } else {
                        binding.updateResults.visibility = View.GONE
                        binding.allUpToDate.visibility = View.VISIBLE
                        binding.pullToRefresh.visibility = View.VISIBLE
                    }
                }
            }
        })

        binding.root.setOnRefreshListener {
            (activity as MainActivity).launch {
                UpdateChecker(requireContext()).checkUpdates()
                _binding?.root?.isRefreshing = false
            }
        }

//        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
//        launch {
//            setUpdateCheckInfo(sharedPrefs)
//        }
//        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private inner class UpdatesPreloadModelProvider(
        val adapter: UpdatesListAdapter
    ) : ListPreloader.PreloadModelProvider<InstallUpdateCheckResult> {
        override fun getPreloadItems(position: Int): MutableList<InstallUpdateCheckResult> {
            if(adapter.availableUpdates.isEmpty())
                return Collections.emptyList()
            else
                return Collections.singletonList(adapter.availableUpdates[position])
        }

        override fun getPreloadRequestBuilder(item: InstallUpdateCheckResult): RequestBuilder<*> {
            return Glide.with(this@UpdatesFragment)
                .load(item.thumbnailUrl)
                .override(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
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