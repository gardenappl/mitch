package ua.gardenapple.itchupdater.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.ItchLibraryParser
import ua.gardenapple.itchupdater.data.ItchLibraryRepository
import ua.gardenapple.itchupdater.data.ItchLibraryViewModel
import ua.gardenapple.itchupdater.databinding.OwnedActivityBinding

class OwnedGamesActivity : AppCompatActivity() {

    companion object {
        const val THUMBNAIL_WIDTH = 315
        const val THUMBNAIL_HEIGHT = 250

        private const val LAST_ANDROID_ONLY_FILTER = "last_android_only"
        private const val DEFAULT_ANDROID_ONLY_FILTER = false
    }

    private lateinit var binding: OwnedActivityBinding

    private lateinit var viewModel: ItchLibraryViewModel
    private val adapter = OwnedGamesAdapter(this)
    
    private var loadJob: Job? = null
    private val repository = ItchLibraryRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = OwnedActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.navigationIcon =
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_arrow_back_24)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // get view model
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return ItchLibraryViewModel(repository) as T
            }
        }).get(ItchLibraryViewModel::class.java)

        binding.ownedItemsList.adapter = adapter
        binding.ownedItemsList.layoutManager = LinearLayoutManager(this)

        load(savedInstanceState?.getBoolean(LAST_ANDROID_ONLY_FILTER, DEFAULT_ANDROID_ONLY_FILTER)
            ?: DEFAULT_ANDROID_ONLY_FILTER)
    }


    private fun load(androidOnly: Boolean) {
        loadJob?.cancel()

        loadJob = lifecycleScope.launch {
            viewModel.getOwnedItems(androidOnly).collectLatest {
                adapter.submitData(it)
            }
        }
    }
}