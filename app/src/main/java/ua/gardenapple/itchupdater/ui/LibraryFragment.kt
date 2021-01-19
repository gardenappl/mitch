package ua.gardenapple.itchupdater.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.FixedPreloadSizeProvider
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.database.game.*
import ua.gardenapple.itchupdater.database.installation.GameInstallation
import ua.gardenapple.itchupdater.databinding.LibraryFragmentBinding
import java.util.*

class LibraryFragment : Fragment() {
    private var _binding: LibraryFragmentBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    private lateinit var pendingViewModel: PendingGameViewModel
    private lateinit var installedViewModel: InstalledGameViewModel
    private lateinit var downloadsViewModel: GameDownloadsViewModel

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


        val pendingAdapter = GameListAdapter(requireActivity(), binding.pendingList, GameRepository.Type.Pending)
        binding.pendingList.adapter = pendingAdapter
        binding.pendingList.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        var sizeProvider = FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        var modelProvider = LibraryPreloadModelProvider(pendingAdapter)
        var preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        binding.pendingList.addOnScrollListener(preloader)

        pendingViewModel = ViewModelProvider(this).get(PendingGameViewModel::class.java)
        pendingViewModel.pendingGames.observe(viewLifecycleOwner, { gameInstalls ->
            gameInstalls?.let { pendingAdapter.gameInstalls = gameInstalls }
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls?.isNotEmpty() == true) {
                        binding.pendingList.visibility = View.VISIBLE
                        binding.pendingLabel.visibility = View.VISIBLE
                    } else {
                        binding.pendingList.visibility = View.GONE
                        binding.pendingLabel.visibility = View.GONE
                    }
                }
            }
        })
        


        val downloadsAdapter = GameListAdapter(requireActivity(), binding.downloadsList, GameRepository.Type.Downloads)
        binding.downloadsList.adapter = downloadsAdapter
        binding.downloadsList.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        sizeProvider = FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        modelProvider = LibraryPreloadModelProvider(downloadsAdapter)
        preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        binding.downloadsList.addOnScrollListener(preloader)

        downloadsViewModel = ViewModelProvider(this).get(GameDownloadsViewModel::class.java)
        downloadsViewModel.gameDownloads.observe(viewLifecycleOwner, { gameInstalls ->
            gameInstalls?.let { downloadsAdapter.gameInstalls = gameInstalls }
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls?.isNotEmpty() == true) {
                        binding.downloadsNothing.visibility = View.VISIBLE
                        binding.downloadsNothing.visibility = View.GONE
                    } else {
                        binding.downloadsNothing.visibility = View.GONE
                        binding.downloadsNothing.visibility = View.VISIBLE
                    }
                }
            }
        })



        val installedAdapter = GameListAdapter(requireActivity(), binding.installedList, GameRepository.Type.Installed)
        binding.installedList.adapter = installedAdapter
        binding.installedList.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        sizeProvider = FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        modelProvider = LibraryPreloadModelProvider(installedAdapter)
        preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        binding.installedList.addOnScrollListener(preloader)

        installedViewModel = ViewModelProvider(this).get(InstalledGameViewModel::class.java)
        installedViewModel.installedGames.observe(viewLifecycleOwner, Observer { gameInstalls ->
            gameInstalls?.let { installedAdapter.gameInstalls = gameInstalls }
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls?.isNotEmpty() == true) {
                        binding.installedList.visibility = View.VISIBLE
                        binding.installedNothing.visibility = View.GONE
                    } else {
                        binding.installedList.visibility = View.GONE
                        binding.installedNothing.visibility = View.VISIBLE
                    }
                }
            }
        })

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

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
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