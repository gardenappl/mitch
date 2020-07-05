package ua.gardenapple.itchupdater.installer

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.webkit.URLUtil
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.PERMISSION_REQUEST_CODE_DOWNLOAD
import ua.gardenapple.itchupdater.ui.PermissionRequestActivity

typealias OnDownloadStartListener = (downloadId: Long) -> Unit

class DownloadRequester {

    companion object {
        private lateinit var currentUrl: String
        private var currentContent: String? = null
        private var currentMimeType: String? = null
        private var currentCallback: OnDownloadStartListener? = null

        fun requestDownload(
            context: Context,
            activity: Activity?,
            url: String,
            contentDisposition: String?,
            mimeType: String?,
            callback: OnDownloadStartListener? = null
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(LOGGING_TAG, "Don't have permission")
                currentUrl = url
                currentContent = contentDisposition
                currentMimeType = mimeType
                currentCallback = callback
                if(activity != null) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE),
                        PERMISSION_REQUEST_CODE_DOWNLOAD
                    )
                } else {
                    val intent = Intent(context, PermissionRequestActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return
            } else {
                Log.d(LOGGING_TAG, "Have permission")
                enqueueDownload(
                    context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager,
                    url,
                    contentDisposition,
                    mimeType,
                    callback
                )
            }
        }

        fun resumeDownload(downloadManager: DownloadManager) {
            Log.d(LOGGING_TAG, "Resuming download")
            enqueueDownload(
                downloadManager,
                currentUrl,
                currentContent,
                currentMimeType,
                currentCallback
            )
        }

        /**
         * Will fail if the user did not provide required permissions.
         */
        private fun enqueueDownload(
            downloadManager: DownloadManager,
            url: String,
            contentDisposition: String?,
            mimeType: String?,
            callback: OnDownloadStartListener?
        ) : Long {
            val downloadRequest = DownloadManager.Request(Uri.parse(url)).apply {
                Log.d(LOGGING_TAG, "Url: $url, contentDisposition: $contentDisposition, mimeType: $mimeType")

                val fileName: String
                //workaround for some devices which forcibly assign .bin file extension
                if(contentDisposition == "application/octet-stream") {
                    fileName = URLUtil.guessFileName(url, contentDisposition, null)
                } else {
                    fileName = URLUtil.guessFileName(url, contentDisposition, contentDisposition)
                    setMimeType(mimeType)
                }

                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Mitch/$fileName")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            }

            val id = downloadManager.enqueue(downloadRequest)
            callback?.invoke(id)
            return id
        }
    }
}