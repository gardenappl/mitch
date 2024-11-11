package garden.appl.mitch.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.FixedPreloadSizeProvider
import garden.appl.mitch.database.game.GameRepository
import garden.appl.mitch.database.game.GameViewModel
import garden.appl.mitch.database.installation.GameInstallation
import garden.appl.mitch.databinding.LibraryFragmentBinding
import java.util.Collections

class LibraryFragment : Fragment() {
    private var _binding: LibraryFragmentBinding? = null
    /** This property is only valid between onCreateView and
     *  onDestroyView. */
    private val binding get() = _binding!!

    companion object {
        //2x of 315x250
        const val THUMBNAIL_WIDTH = 630
        const val THUMBNAIL_HEIGHT = 500
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = LibraryFragmentBinding.inflate(inflater, container, false)
        val view = binding.root

        addList(
            GameRepository.Type.Pending,
            binding.pendingList,
            GameViewModel.Pending::class.java
        ) { gameInstalls ->
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls.isNotEmpty()) {
                        binding.pendingLabel.visibility = View.VISIBLE
                        binding.pendingList.visibility = View.VISIBLE
                        binding.pendingDivider.visibility = View.VISIBLE
                    } else {
                        binding.pendingLabel.visibility = View.GONE
                        binding.pendingList.visibility = View.GONE
                        binding.pendingDivider.visibility = View.GONE
                    }
                }
            }
        }

        addList(
            GameRepository.Type.Installed,
            binding.installedList,
            GameViewModel.Installed::class.java
        ) { gameInstalls ->
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls.isNotEmpty()) {
                        binding.installedList.visibility = View.VISIBLE
                        binding.installedNothing.visibility = View.GONE
                    } else {
                        binding.installedList.visibility = View.GONE
                        binding.installedNothing.visibility = View.VISIBLE
                    }
                }
            }
        }

        addList(
            GameRepository.Type.WebCached,
            binding.webCachedList,
            GameViewModel.WebCached::class.java
        ) { gameInstalls ->
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls.isNotEmpty()) {
                        binding.webCachedLabel.visibility = View.VISIBLE
                        binding.webCachedList.visibility = View.VISIBLE
                        binding.installedNothingWebWrapper.visibility = View.GONE
                    } else {
                        binding.webCachedLabel.visibility = View.GONE
                        binding.webCachedList.visibility = View.GONE
                        binding.installedNothingWebWrapper.visibility = View.VISIBLE
                    }
                }
            }
        }

        addList(
            GameRepository.Type.Downloads,
            binding.downloadsList,
            GameViewModel.Downloads::class.java
        ) { gameInstalls ->
            view.post {
                _binding?.let { binding ->
                    if (gameInstalls.isNotEmpty()) {
                        binding.downloadsList.visibility = View.VISIBLE
                        binding.downloadsNothing.visibility = View.GONE
                    } else {
                        binding.downloadsList.visibility = View.GONE
                        binding.downloadsNothing.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Owned items
        binding.ownedButton.setOnClickListener { 
            val intent = Intent(requireContext(), OwnedGamesActivity::class.java)
            startActivity(intent)
        }

        return view
    }

    private fun <T : GameViewModel> addList(
        type: GameRepository.Type,
        list: RecyclerView,
        viewModelClass: Class<T>,
        onObserve: (List<GameInstallation>) -> Unit
    ) {
        val adapter = LibraryAdapter(requireActivity(), list, type)
        list.adapter = adapter
        list.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        val sizeProvider =
            FixedPreloadSizeProvider<GameInstallation>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        val modelProvider = LibraryPreloadModelProvider(adapter)
        val preloader = RecyclerViewPreloader(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        list.addOnScrollListener(preloader)

        val viewModel = ViewModelProvider(this).get(viewModelClass)
        viewModel.games.observe(viewLifecycleOwner) { gameInstalls ->
            gameInstalls?.let {
                adapter.gameInstalls = gameInstalls
                onObserve(gameInstalls)
            }
        }
    }

    private inner class LibraryPreloadModelProvider(
        val adapter: LibraryAdapter
    ) : ListPreloader.PreloadModelProvider<GameInstallation> {
        override fun getPreloadItems(position: Int): MutableList<GameInstallation> {
            if (adapter.gameInstalls.isEmpty())
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