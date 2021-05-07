package ua.gardenapple.itchupdater.install


import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.net.toFile
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.FILE_PROVIDER
import ua.gardenapple.itchupdater.Utils


/**
 * A transparent activity as a wrapper around Android's PackageInstaller Intents.
 * Copied from F-Droid client:
 * https://gitlab.com/fdroid/fdroidclient/-/blob/6e2b258eee639a43213fe9c6f413780c29fc0e8b/app/src/main/java/org/fdroid/fdroid/installer/DefaultInstallerActivity.java
 */
class NativeInstallerActivity : FragmentActivity() {
    companion object {
        private const val LOGGING_TAG = "DefaultInstallerActivit"
        const val ACTION_INSTALL_PACKAGE =
            "ua.gardenapple.itchupdater.install.NativeInstaller.INSTALL_PACKAGE"
        private const val REQUEST_CODE_INSTALL = 0

        const val EXTRA_INSTALL_ID = "install_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ACTION_INSTALL_PACKAGE == intent.action) {
            val installId = intent.extras!!.getLong(EXTRA_INSTALL_ID)
            val uri = Utils.getIntentUriForFile(this, intent.data!!.toFile(), FILE_PROVIDER)
            installPackage(uri, installId)
        } else {
            throw IllegalStateException("Intent action not specified!")
        }
    }

    @SuppressLint("InlinedApi")
    private fun installPackage(uri: Uri, installId: Long) {
        Log.d(LOGGING_TAG, "Installing ID: $installId, uri: $uri")
        // https://code.google.com/p/android/issues/detail?id=205827
        if (Build.VERSION.SDK_INT < 24
            && ContentResolver.SCHEME_FILE != uri.scheme
        ) {
            throw RuntimeException("PackageInstaller < Android N only supports file scheme!")
        }
        if (Build.VERSION.SDK_INT >= 24
            && ContentResolver.SCHEME_CONTENT != uri.scheme
        ) {
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
                Installations.onInstallResult(applicationContext, installId, null,
                    uri.lastPathSegment!!, PackageInstaller.STATUS_FAILURE)
            }
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(LOGGING_TAG, "Result code: $resultCode, data: $data")
        Log.d(LOGGING_TAG, "Extras: ${Utils.toString(data?.extras)}")
        
        val installId = Utils.getLong(intent.extras!!, EXTRA_INSTALL_ID)!!
        val apkName = intent.data!!.lastPathSegment!!
        when (requestCode) {
            REQUEST_CODE_INSTALL -> when (resultCode) {
                RESULT_OK -> runBlocking(Dispatchers.IO) {
                    val packageInfo =
                        packageManager.getPackageArchiveInfo(intent.data!!.path!!, 0)!!
                    Installations.onInstallResult(applicationContext, installId,
                        packageInfo.packageName, apkName, PackageInstaller.STATUS_SUCCESS)
                }
                RESULT_CANCELED -> runBlocking(Dispatchers.IO) {
                    Installations.onInstallResult(applicationContext, installId, null,
                        apkName, PackageInstaller.STATUS_FAILURE_ABORTED)
                }
                //RESULT_FIRST_USER // AOSP returns AppCompatActivity.RESULT_FIRST_USER on error
                else -> runBlocking(Dispatchers.IO) {
                    Installations.onInstallResult(applicationContext, installId, null,
                        apkName, PackageInstaller.STATUS_FAILURE)
                }
            }
            else -> throw RuntimeException("Invalid request code!")
        }

        // after doing the broadcasts, finish this transparent wrapper activity
        finish()
    }
}
