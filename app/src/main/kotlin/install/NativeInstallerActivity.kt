package garden.appl.mitch.install


import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.net.toFile
import androidx.fragment.app.FragmentActivity
import garden.appl.mitch.FILE_PROVIDER
import garden.appl.mitch.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File


/**
 * A transparent activity as a wrapper around Android's PackageInstaller Intents.
 * Copied from F-Droid client:
 * https://gitlab.com/fdroid/fdroidclient/-/blob/6e2b258eee639a43213fe9c6f413780c29fc0e8b/app/src/main/java/org/fdroid/fdroid/installer/DefaultInstallerActivity.java
 */
class NativeInstallerActivity : FragmentActivity() {
    companion object {
        private const val LOGGING_TAG = "NativeInstallerActivity"
        const val ACTION_INSTALL_PACKAGE =
            "ua.gardenapple.itchupdater.install.NativeInstaller.INSTALL_PACKAGE"
        private const val REQUEST_CODE_INSTALL = 0

        const val EXTRA_INSTALL_ID = "install_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == ACTION_INSTALL_PACKAGE) {
            Log.d(LOGGING_TAG, "Starting installation...")
            val installId = intent.extras!!.getLong(EXTRA_INSTALL_ID)

            val file = intent.data!!.toFile()
            runBlocking(Dispatchers.IO) {
                Installations.tryUpdatePendingInstallData(this@NativeInstallerActivity,
                    installId, file)
            }

            installPackage(file, installId)
        } else {
            throw IllegalStateException("Intent action not specified!")
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("ObsoleteSdkInt")
    private fun installPackage(file: File, installId: Long) {
        val uri = Utils.getIntentUriForFile(this, file, FILE_PROVIDER)

        Log.d(LOGGING_TAG, "Installing ID: $installId, uri: $uri")
        // https://code.google.com/p/android/issues/detail?id=205827
        if (Build.VERSION.SDK_INT < 24 && ContentResolver.SCHEME_FILE != uri.scheme) {
            throw RuntimeException("PackageInstaller < Android N only supports file scheme!")
        }
        if (Build.VERSION.SDK_INT >= 24 && ContentResolver.SCHEME_CONTENT != uri.scheme) {
            throw RuntimeException("PackageInstaller >= Android N only supports content scheme!")
        }
        val intent = Intent()

        // Note regarding EXTRA_NOT_UNKNOWN_SOURCE:
        // works only when being installed as system-app
        // https://code.google.com/p/android/issues/detail?id=42253
        if (Build.VERSION.SDK_INT < 16) {
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = uri
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.putExtra(Intent.EXTRA_ALLOW_REPLACE, true)
        } else if (Build.VERSION.SDK_INT < 24) {
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = uri
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        } else { // Android N
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = uri
            // grant READ permission for this content Uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_INSTALL)
        } catch (e: ActivityNotFoundException) {
            Log.e(LOGGING_TAG, "ActivityNotFoundException", e)
            runBlocking(Dispatchers.IO) {
                Installations.onInstallResult(applicationContext, installId, file.name,
                    null, file, PackageInstaller.STATUS_FAILURE)
            }
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(LOGGING_TAG, "Result code: $resultCode, data: $data")
        Log.d(LOGGING_TAG, "Extras: ${Utils.toString(data?.extras)}")
        

        val installId = Utils.getLong(intent.extras!!, EXTRA_INSTALL_ID)!!
        val apk = intent.data!!.toFile()
        when (requestCode) {
            REQUEST_CODE_INSTALL -> when (resultCode) {
                RESULT_OK -> runBlocking(Dispatchers.IO) {
                    Log.d(LOGGING_TAG, "OK, intent data: ${intent.data}")
                    Installations.onInstallResult(applicationContext, installId, apk.name, null,
                        apk, PackageInstaller.STATUS_SUCCESS)
                }
                RESULT_CANCELED -> runBlocking(Dispatchers.IO) {
                    Installations.onInstallResult(applicationContext, installId, apk.name, null,
                        apk, PackageInstaller.STATUS_FAILURE_ABORTED)
                }
                else -> runBlocking(Dispatchers.IO) {
                    // Undocumented AOSP stuff
                    when (data?.extras?.getLong("android.intent.extra.INSTALL_RESULT")) {
                        -4L -> Installations.onInstallResult(applicationContext, installId, apk.name, null,
                                apk, PackageInstaller.STATUS_FAILURE_STORAGE)
                        else -> Installations.onInstallResult(applicationContext, installId, apk.name, null,
                            apk, PackageInstaller.STATUS_FAILURE)
                    }
                }
            }
            else -> throw RuntimeException("Invalid request code!")
        }

        // after doing the broadcasts, finish this transparent wrapper activity
        finish()
    }
}
