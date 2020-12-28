package ua.gardenapple.itchupdater.ui

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import kotlinx.android.synthetic.main.library_fragment.*
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.game.*
import java.util.*

class LibraryFragment : Fragment() {
    private lateinit var pendingViewModel: PendingGameViewModel
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


        val pendingList = view.findViewById<RecyclerView>(R.id.pending_list)
        val pendingLabel = view.findViewById<TextView>(R.id.pending_label)
        val pendingAdapter = GameListAdapter(requireContext(), pendingList, GameRepository.Type.Pending)
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
            view?.post {
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
        val downloadsAdapter = GameListAdapter(requireContext(), downloadsList, GameRepository.Type.Downloads)
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
        val installedAdapter = GameListAdapter(requireContext(), installedList, GameRepository.Type.Installed)
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