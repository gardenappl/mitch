package ua.gardenapple.itchupdater.files

import android.content.Context
import android.util.Log
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.installer.DownloadFileListener
import java.io.File

/**
 * This listener responds to finished file downloads from Fetch.
 */
class MitchFetchListener(private val context: Context, private val fetchDownloader: DownloaderFetch)
    : FetchListener, DownloadFileListener() {

    companion object {
        private const val LOGGING_TAG = "FileDownloadListener"
    }

    override fun onAdded(download: Download) {}

    override fun onCancelled(download: Download) {
        Log.d(LOGGING_TAG, "Cancelled ID: ${download.id}")
        fetchDownloader.removeFetchDownload(download.id)
    }

    override fun onCompleted(download: Download) {
        super.onCompleted(context, download.file, fetchDownloader.getUploadId(download),
            download.id.toLong())

        fetchDownloader.removeFetchDownload(download.id)
    }

    override fun onDeleted(download: Download) {
        //Should not happen all by itself, but let's handle this just in case
        Log.w(LOGGING_TAG, "Deleted download! $download")
        runBlocking(Dispatchers.IO) {
            Mitch.databaseHandler.onDownloadFailed(download.id.toLong())
        }
    }

    override fun onDownloadBlockUpdated(
        download: Download,
        downloadBlock: DownloadBlock,
        totalBlocks: Int
    ) {}

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        super.onError(context, File(download.file), download.id.toLong(),
            fetchDownloader.getUploadId(download), error.name)

        fetchDownloader.removeFetchDownload(download.id)
    }

    override fun onPaused(download: Download) {}

    override fun onProgress(
        download: Download,
        etaInMilliSeconds: Long,
        downloadedBytesPerSecond: Long
    ) {
        onProgress(
            context,
            File(download.file),
            download.id.toLong(),
            fetchDownloader.getUploadId(download),
            download.progress,
            download.etaInMilliSeconds
        )
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {}

    override fun onRemoved(download: Download) {}

    override fun onResumed(download: Download) {}

    override fun onStarted(
        download: Download,
        downloadBlocks: List<DownloadBlock>,
        totalBlocks: Int
    ) {
        super.onProgress(
            context,
            File(download.file),
            download.id.toLong(),
            fetchDownloader.getUploadId(download),
            etaInMilliSeconds = null,
            progressPercent = null
        )
    }

    override fun onWaitingNetwork(download: Download) {}
}