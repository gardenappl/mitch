package ua.gardenapple.itchupdater.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

abstract class MitchActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(MitchContextWrapper.wrap(newBase, "nb-NO"))
    }
}