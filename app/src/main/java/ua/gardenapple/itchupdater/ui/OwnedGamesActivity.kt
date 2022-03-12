package ua.gardenapple.itchupdater.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.data.ItchLibraryRepository
import ua.gardenapple.itchupdater.data.ItchLibraryViewModel
import ua.gardenapple.itchupdater.databinding.OwnedActivityBinding

class OwnedGamesActivity : MitchActivity() {

    companion object {
        const val THUMBNAIL_WIDTH = 315
        const val THUMBNAIL_HEIGHT = 250

        private const val LAST_SEARCH_QUERY = "last_search"
        private const val LAST_ANDROID_ONLY_FILTER = "ua.gardenapple.itchupdater.lastupdatecheck.last_android_only"
        private const val DEFAULT_ANDROID_ONLY_FILTER = false
    }

    private lateinit var binding: OwnedActivityBinding

    private lateinit var viewModel: ItchLibraryViewModel
    private val adapter = OwnedGamesAdapter(this)
    private var loadJob: Job? = null
    private val repository = ItchLibraryRepository()

    private var androidOnlyFilter: Boolean = DEFAULT_ANDROID_ONLY_FILTER
    private var searchString: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = OwnedActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.navigationIcon =
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_arrow_back_24)
        binding.toolbar.title = getString(R.string.library_category_owned)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // get view model
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ItchLibraryViewModel(repository) as T
            }
        }).get(ItchLibraryViewModel::class.java)

        binding.ownedItemsList.adapter = adapter.withLoadStateFooter(
            OwnedGamesLoadStateAdapter { adapter.retry() }
        )
        adapter.addLoadStateListener { loadState ->
            showListEmpty(loadState.refresh is LoadState.NotLoading &&
                    loadState.refresh.endOfPaginationReached && adapter.itemCount == 0)

            binding.noOwnedGames.setText(
                if (androidOnlyFilter) 
                    R.string.library_no_android_games 
                else 
                    R.string.library_no_games
            )

            if (loadState.source.refresh is LoadState.NotLoading) {
                binding.ownedItemsList.visibility = View.VISIBLE
                binding.loadStateConstraintLayout.visibility = View.GONE
            } else {
                binding.ownedItemsList.visibility = View.GONE
                binding.loadStateConstraintLayout.visibility = View.VISIBLE
                OwnedGamesLoadStateAdapter.bind(binding.loadStateLayout, this, loadState.source.refresh) {
                    adapter.retry()
                }
            }
        }
        binding.ownedItemsList.layoutManager = LinearLayoutManager(this)

        androidOnlyFilter =
            savedInstanceState?.getBoolean(LAST_ANDROID_ONLY_FILTER, DEFAULT_ANDROID_ONLY_FILTER)
                ?: PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                    LAST_ANDROID_ONLY_FILTER, DEFAULT_ANDROID_ONLY_FILTER)
        searchString = savedInstanceState?.getString(LAST_SEARCH_QUERY) ?: ""

        loadItems(searchString, androidOnlyFilter)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(LAST_ANDROID_ONLY_FILTER, androidOnlyFilter)
    }


    private fun loadItems(searchString: String, androidOnly: Boolean) {
        loadJob?.cancel()

        loadJob = lifecycleScope.launch {
            viewModel.getOwnedItems(searchString, androidOnly).collectLatest {
                adapter.submitData(it)
            }
        }

        lifecycleScope.launch {
            adapter.loadStateFlow
                .distinctUntilChangedBy { it.refresh }
                .filter { it.refresh is LoadState.NotLoading }
                .collect { binding.ownedItemsList.scrollToPosition(0) }
        }
    }
    
    private fun showListEmpty(isEmpty: Boolean) {
        binding.emptyListConstraintLayout.isVisible = isEmpty

        if (isEmpty) {
            binding.noOwnedGames.setText(
                if (androidOnlyFilter)
                    R.string.library_no_android_games
                else
                    R.string.library_no_games
            )
            binding.goToStoreButton.setOnClickListener {
                startActivity(Intent(
                    Intent.ACTION_VIEW,
                    if (androidOnlyFilter)
                        ItchWebsiteUtils.STORE_ANDROID_PAGE_URI
                    else
                        ItchWebsiteUtils.STORE_PAGE_URI,
                    this,
                    MainActivity::class.java
                ))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.owned_actions, menu)
        menu.findItem(R.id.only_android).isChecked = androidOnlyFilter

        val searchView: SearchView = menu.findItem(R.id.games_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    searchString = newText
                    this@OwnedGamesActivity.loadItems(it, androidOnlyFilter)
                }
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.only_android -> {
                androidOnlyFilter = !item.isChecked
                loadItems(searchString, androidOnlyFilter)
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                sharedPrefs.edit().run {
                    putBoolean(LAST_ANDROID_ONLY_FILTER, androidOnlyFilter)
                    apply()
                }
                item.isChecked = androidOnlyFilter
                return true
            }
        }
        return false
    }
}