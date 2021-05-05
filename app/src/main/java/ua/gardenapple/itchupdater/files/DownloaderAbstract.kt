package ua.gardenapple.itchupdater.files

import android.content.Context
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.File

interface DownloaderAbstract {
    /**
     * All previous downloads for the same uploadId must be cancelled at this point
     * @return error message to present to the user, null if successful
     */
    suspend fun requestDownload(context: Context, url: String,
                                         file: File, install: Installation): String?

    /**
     * @return true if cancellation was clean, false if there was an error
     */
    suspend fun cancel(context: Context, downloadId: Int): Boolean

    suspend fun checkIsDownloading(context: Context, downloadId: Int): Boolean
}