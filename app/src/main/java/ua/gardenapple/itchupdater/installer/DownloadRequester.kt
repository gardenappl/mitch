package ua.gardenapple.itchupdater.installer

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.webkit.URLUtil
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.PERMISSION_REQUEST_CODE_DOWNLOAD

class DownloadRequester {

    companion object {
        lateinit var currentUrl: String
        lateinit var currentContent: String
        lateinit var currentMimeType: String

        fun requestDownload(context: Context, activity: Activity?, url: String, contentDisposition: String, mimeType: String) {
            if (context.packageManager.checkPermission(context.packageName, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if(activity != null) {
                    currentUrl = url
                    currentContent = contentDisposition
                    currentMimeType = mimeType
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        PERMISSION_REQUEST_CODE_DOWNLOAD
                    )
                } else {
                    //TODO: PermissionRequestActivity
                }
                return
            } else {
                doDownload(
                    context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager,
                    url,
                    contentDisposition,
                    mimeType
                )
            }
        }

        fun resumeDownload(downloadManager: DownloadManager) {
            Log.d(LOGGING_TAG, "Resuming download")
            doDownload(
                downloadManager,
                currentUrl,
                currentContent,
                currentMimeType
            )
        }

        private fun doDownload(downloadManager: DownloadManager, url: String, contentDisposition: String, mimeType: String) {
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

            //TODO: store id and manage multiple downloads
            val id = downloadManager.enqueue(downloadRequest)
        }
    }
}