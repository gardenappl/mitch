package ua.gardenapple.itchupdater.install

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.net.toFile
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.databinding.SessionInstallerActivityBinding
import ua.gardenapple.itchupdater.ui.MitchActivity
import java.lang.IllegalArgumentException

/**
 * Loads the APK into the [SessionInstaller] while displaying a loading screen.
 * This activity is not strictly necessary but nice to have for UI purposes.
 */
class SessionInstallerActivity : MitchActivity(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_DOWNLOAD_ID = "DOWNLOAD_ID"
        const val EXTRA_APP_NAME = "APP_NAME"

        private const val LOGGING_TAG = "SessionInstallActivity"
    }

    private lateinit var binding: SessionInstallerActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SessionInstallerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = ""
    }

    override fun onStart() {
        super.onStart()

        val downloadId = Utils.getInt(intent.extras!!, EXTRA_DOWNLOAD_ID)!!

        launch {
            val db = AppDatabase.getDatabase(this@SessionInstallerActivity)
            title = db.gameDao.getNameForPendingInstallWithDownloadId(downloadId)

            try {
                Installations.sessionInstaller.doInstall(
                    this@SessionInstallerActivity,
                    downloadId, intent.data!!.toFile()
                )

                this@SessionInstallerActivity.finish()
            } catch (e: SessionInstaller.NotEnoughSpaceException) {
                binding.progressBar2.visibility = View.GONE
                binding.textView.setText(R.string.dialog_installer_no_space)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }
}