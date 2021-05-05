package ua.gardenapple.itchupdater.files

import android.content.Context
import android.util.Log
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.Extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.File
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class DownloaderFetch(private val fetch: Fetch) : DownloaderAbstract {
    companion object {
        private const val LOGGING_TAG = "FetchDownloadFileMan"

        private const val DOWNLOAD_EXTRA_UPLOAD_ID = "uploadId"
    }

    override suspend fun requestDownload(context: Context, url: String,
                                         file: File, install: Installation): String? {
        val request = Request(url, file.path).apply {
            this.networkType = NetworkType.ALL
            this.extras = Extras(Collections.singletonMap(
                DOWNLOAD_EXTRA_UPLOAD_ID, install.uploadId.toString()
            ))
            this.enqueueAction = EnqueueAction.REPLACE_EXISTING
        }

        val error = suspendCoroutine<Error?> { cont ->
            fetch.enqueue(request, { updatedRequest ->
                Log.d(LOGGING_TAG, "Enqueued ${updatedRequest.id}")

                install.downloadOrInstallId = updatedRequest.id.toLong()
                install.status = Installation.STATUS_DOWNLOADING
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    db.installDao.insert(install)
                }
                cont.resume(null)
            }, { error ->
                Log.e(LOGGING_TAG, error.name, error.throwable)
                cont.resume(error)
            })
        }
        return error?.name
    }

    override suspend fun cancel(context: Context, downloadId: Int): Boolean {
        return suspendCoroutine { cont ->
            fetch.remove(downloadId, {
                cont.resume(true)
            }) { error ->
                Log.e(
                    LOGGING_TAG, "Error while cancelling/removing download: ${error.name}",
                    error.throwable
                )
                cont.resume(false)
            }
        }
    }

    override suspend fun checkIsDownloading(context: Context, downloadId: Int): Boolean {
        return suspendCoroutine { cont ->
            fetch.getDownload(downloadId) { download ->
                cont.resume(download != null)
            }
        }
    }

    /**
     * Remove all downloads from Fetch's internal database and delete pending files
     */
    fun deleteAllDownloads() {
        fetch.deleteAll()
    }

    /**
     * Remove download from Fetch's internal database
     */
    fun removeFetchDownload(downloadId: Int) {
        fetch.remove(downloadId)
    }

    fun getUploadId(download: Download): Int {
        return Integer.parseInt(download.extras.getString(DOWNLOAD_EXTRA_UPLOAD_ID, ""))
    }
}