package ua.gardenapple.itchupdater.client.web

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.webkit.URLUtil
import ua.gardenapple.itchupdater.LOGGING_TAG
import ua.gardenapple.itchupdater.MainActivity
import ua.gardenapple.itchupdater.PERMISSION_REQUEST_CODE_DOWNLOAD

class DownloadRequester {

    companion object {
        lateinit var currentUrl: String
        lateinit var currentContent: String
        lateinit var currentMimeType: String

        fun requestDownload(activity: MainActivity, url: String, contentDisposition: String, mimeType: String) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(LOGGING_TAG, activity.getWebView().originalUrl);
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE_DOWNLOAD
                )
                currentUrl = url
                currentContent = contentDisposition
                currentMimeType = mimeType
                return
            } else {
                doDownload(activity.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager, url, contentDisposition, mimeType)
            }
        }

        fun resumeDownload(downloadManager: DownloadManager) {
            doDownload(downloadManager, currentUrl, currentContent, currentMimeType)
        }

        private fun doDownload(downloadManager: DownloadManager, url: String, contentDisposition: String, mimeType: String) {
            val downloadRequest = DownloadManager.Request(Uri.parse(url)).apply {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"itchAnd/" + fileName)
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            }

            val id = downloadManager.enqueue(downloadRequest)
        }
    }
}