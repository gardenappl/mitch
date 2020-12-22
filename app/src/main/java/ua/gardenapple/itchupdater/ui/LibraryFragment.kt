package ua.gardenapple.itchupdater.ui

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.FixedPreloadSizeProvider
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.game.GameDownloadsViewModel
import ua.gardenapple.itchupdater.database.game.GameRepository
import ua.gardenapple.itchupdater.database.game.InstalledGameViewModel
import java.util.*

class LibraryFragment : Fragment() {
    private lateinit var installedViewModel: InstalledGameViewModel
    private lateinit var downloadsViewModel: GameDownloadsViewModel

    companion object {
        private const val LOGGING_TAG = "LibraryFragment"

        //2x of 315x250
        const val THUMBNAIL_WIDTH = 650
        const val THUMBNAIL_HEIGHT = 500
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.library_fragment, container, false)



        val downloadsList = view.findViewById<RecyclerView>(R.id.downloads_list)
        val downloadsAdapter = GameListAdapter(requireContext(), downloadsList, GameRepository.Type.Downloads)
        downloadsList.adapter = downloadsAdapter
        downloadsList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        var sizeProvider = FixedPreloadSizeProvider<Game>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        var modelProvider = LibraryPreloadModelProvider(downloadsAdapter)
        var preloader = RecyclerViewPreloader<Game>(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        downloadsList.addOnScrollListener(preloader)

        downloadsViewModel = ViewModelProvider(this).get(GameDownloadsViewModel::class.java)
        downloadsViewModel.gameDownloads.observe(viewLifecycleOwner, Observer { games ->
            games?.let { downloadsAdapter.games = games }
        })



        val installedList = view.findViewById<RecyclerView>(R.id.installed_list)
        val installedAdapter = GameListAdapter(requireContext(), installedList, GameRepository.Type.Installed)
        installedList.adapter = installedAdapter
        installedList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        //Glide thumbnail handling
        sizeProvider = FixedPreloadSizeProvider<Game>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        modelProvider = LibraryPreloadModelProvider(installedAdapter)
        preloader = RecyclerViewPreloader<Game>(
            Glide.with(this), modelProvider, sizeProvider, 6
        )
        installedList.addOnScrollListener(preloader)

        installedViewModel = ViewModelProvider(this).get(InstalledGameViewModel::class.java)
        installedViewModel.installedGames.observe(viewLifecycleOwner, Observer { games ->
            games?.let { installedAdapter.games = games }
        })

        return view
    }

    private inner class LibraryPreloadModelProvider(
        val adapter: GameListAdapter
    ) : ListPreloader.PreloadModelProvider<Game> {
        override fun getPreloadItems(position: Int): MutableList<Game> {
            if(adapter.games.isEmpty())
                return Collections.emptyList()
            else
                return Collections.singletonList(adapter.games[position])
        }

        override fun getPreloadRequestBuilder(item: Game): RequestBuilder<*>? {
            return Glide.with(this@LibraryFragment)
                .load(item.thumbnailUrl)
                .override(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)

        }
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

//        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
//        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

//        if (currentNightMode != newNightMode) {
            //Re-attaching a fragment will redraw its UI

            (activity as MainActivity).supportFragmentManager.beginTransaction().apply {
                detach(this@LibraryFragment)
                attach(this@LibraryFragment)
                commit()
            }
//        }
    }
}