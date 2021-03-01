package ua.gardenapple.itchupdater.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.ItchLibraryParser
import ua.gardenapple.itchupdater.databinding.ActivityOwnedBinding

class OwnedGamesActivity : AppCompatActivity() {
    lateinit var binding: ActivityOwnedBinding
    private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOwnedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.navigationIcon =
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_arrow_back_24)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        GlobalScope.launch {
            val items = ItchLibraryParser.parsePage(0)!!
            for (item in items) {
                Log.d(Mitch.LOGGING_TAG, item.toString())
            }
        }
    }
}