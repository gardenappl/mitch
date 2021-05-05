package ua.gardenapple.itchupdater.files

import android.content.Context
import ua.gardenapple.itchupdater.database.installation.Installation

abstract class AbstractDownloader {
    /**
     * All previous downloads for the same uploadId must be cancelled at this point
     * @return error message to present to the user, null if successful
     */
    abstract suspend fun requestDownload(context: Context, url: String,
                                       filePath: String, install: Installation): String?

    /**
     * @return true if cancellation was clean, false if there was an error
     */
    abstract suspend fun requestCancel(downloadId: Int): Boolean

    abstract suspend fun checkIsDownloading(downloadId: Int): Boolean
}