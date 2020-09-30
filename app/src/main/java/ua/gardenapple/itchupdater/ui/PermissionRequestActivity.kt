package ua.gardenapple.itchupdater.ui

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import ua.gardenapple.itchupdater.PERMISSION_REQUEST_CODE_DOWNLOAD
import ua.gardenapple.itchupdater.installer.DownloadRequester

class PermissionRequestActivity : Activity(), ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        const val LOGGING_TAG = "PermissionRequest"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOGGING_TAG, "Created")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PERMISSION_REQUEST_CODE_DOWNLOAD
        )
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(LOGGING_TAG, "Got result")
        when (requestCode) {
            PERMISSION_REQUEST_CODE_DOWNLOAD -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(LOGGING_TAG, "Resuming download...")
                    DownloadRequester.resumeDownload(getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                } else {
                    Log.d(LOGGING_TAG, "Permission not granted")
                }
            }
        }
        finish()
    }
}