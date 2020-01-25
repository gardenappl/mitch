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
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.game.GameDownloadsViewModel
import ua.gardenapple.itchupdater.database.game.InstalledGameViewModel

class LibraryFragment : Fragment() {
    private lateinit var installedViewModel: InstalledGameViewModel
    private lateinit var downloadsViewModel: GameDownloadsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.library_fragment, container, false)


        Log.d(LOGGING_TAG, "DOing studff..")

        val downloadsList = view.findViewById<RecyclerView>(R.id.downloads_list)
        val downloadsAdapter = GameListAdapter(context!!)
        downloadsList.adapter = downloadsAdapter
        downloadsList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        downloadsViewModel = ViewModelProvider(this).get(GameDownloadsViewModel::class.java)
        downloadsViewModel.allGames.observe(viewLifecycleOwner, Observer { games ->
            Log.d(LOGGING_TAG, "Downloaded games observe!")
            games?.let { Log.d(LOGGING_TAG, "${games.size}"); downloadsAdapter.setGames(games) }
        })

        Log.d(LOGGING_TAG, "DOing more studff..")

        val installedList = view.findViewById<RecyclerView>(R.id.installed_list)
        val installedAdapter = GameListAdapter(context!!)
        installedList.adapter = installedAdapter
        installedList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        Log.d(LOGGING_TAG, "DOing even more studff..")
        installedViewModel = ViewModelProvider(this).get(InstalledGameViewModel::class.java)
        installedViewModel.allGames.observe(viewLifecycleOwner, Observer { games ->
            Log.d(LOGGING_TAG, "Installed games observe!")
            games?.let { Log.d(LOGGING_TAG, "${games.size}"); installedAdapter.setGames(games) }
        })

        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}