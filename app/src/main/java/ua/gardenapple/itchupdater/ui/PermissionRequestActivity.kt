package ua.gardenapple.itchupdater.ui

import android.app.Activity
import androidx.core.app.ActivityCompat

class PermissionRequestActivity : Activity(), ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        private const val LOGGING_TAG = "PermissionRequest"
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        Log.d(LOGGING_TAG, "Created")
//        ActivityCompat.requestPermissions(
//            this,
//            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
//            PERMISSION_REQUEST_CODE_DOWNLOAD
//        )
//    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        Log.d(LOGGING_TAG, "Got result")
//        when (requestCode) {
//            PERMISSION_REQUEST_CODE_DOWNLOAD -> {
//                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Log.d(LOGGING_TAG, "Resuming download...")
//                    DownloadFileManager.resumeDownload(getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
//                } else {
//                    Log.d(LOGGING_TAG, "Permission not granted")
//                }
//            }
//        }
        finish()
    }
}