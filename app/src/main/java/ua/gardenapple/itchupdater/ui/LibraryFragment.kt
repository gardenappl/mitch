package ua.gardenapple.itchupdater.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.library_fragment.view.*
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.game.GameRepository
import ua.gardenapple.itchupdater.database.game.GameViewModel

class LibraryFragment : Fragment() {
    private lateinit var gameViewModel: GameViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.library_fragment, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.gameList)
        val adapter = GameListAdapter(requireContext())
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        gameViewModel = ViewModelProvider(this).get(GameViewModel::class.java)
        gameViewModel.allGames.observe(viewLifecycleOwner, Observer { games ->
            games?.let { adapter.setGames(games) }
        })

        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}