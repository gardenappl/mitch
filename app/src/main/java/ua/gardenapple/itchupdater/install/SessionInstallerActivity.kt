package ua.gardenapple.itchupdater.install

import android.os.Bundle
import android.util.Log
import androidx.core.net.toFile
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.ui.MitchActivity
import java.lang.IllegalArgumentException

/**
 * Loads the APK into the [SessionInstaller] while displaying a loading screen.
 * This activity is not strictly necessary but nice to have for UI purposes.
 */
class SessionInstallerActivity : MitchActivity(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_DOWNLOAD_ID = "DOWNLOAD_ID"

        private const val LOGGING_TAG = "SessionInstallActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.session_installer_activity)
    }

    override fun onStart() {
        super.onStart()

        val downloadId = Utils.getInt(intent.extras!!, EXTRA_DOWNLOAD_ID)!!

        launch {
            Installations.sessionInstaller.doInstall(this@SessionInstallerActivity,
                downloadId, intent.data!!.toFile())

            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@SessionInstallerActivity)
                val installs = db.installDao.getAllKnownInstallationsSync()
                Log.d(LOGGING_TAG, "Known installs:")
                for (install in installs)
                    Log.d(LOGGING_TAG, install.toString())
            }

            this@SessionInstallerActivity.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }
}