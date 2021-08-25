package ua.gardenapple.itchupdater.files

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Downloader : DownloadFileListener() {
    private const val WORKER_URL = "url"
    private const val WORKER_FILE_PATH = "path"
    private const val WORKER_DOWNLOAD_ID = "download_id"
    private const val WORKER_UPLOAD_ID = "upload_id"
    private const val TAG_WORKER = "MITCH_DOWN"

    private const val LOGGING_TAG = "WorkerDownloader"

    @Synchronized
    private fun getUnusedDownloadId(context: Context): Int {
        for (i in 0..Int.MAX_VALUE) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(getWorkName(i))
                .get()
            if (workInfos.isEmpty())
                return i
        }
        throw RuntimeException("Could not find free download ID for DownloaderWorker")
    }

    private fun getWorkName(downloadId: Int): String = "MITCH_DOWN_$downloadId"

    suspend fun requestDownload(context: Context, url: String, file: File,
                                install: Installation): String? {
        val downloadId = getUnusedDownloadId(context)
        Log.d(LOGGING_TAG, "Download ID: $downloadId")
        install.downloadOrInstallId = downloadId.toLong()
        install.status = Installation.STATUS_DOWNLOADING

        val db = AppDatabase.getDatabase(context)
        db.installDao.insert(install)

        val downloadRequest =
            OneTimeWorkRequestBuilder<Worker>().run {
                setInputData(workDataOf(
                    Pair(WORKER_URL, url),
                    Pair(WORKER_FILE_PATH, file.path),
                    Pair(WORKER_DOWNLOAD_ID, downloadId),
                    Pair(WORKER_UPLOAD_ID, install.uploadId)
                ))
                addTag(TAG_WORKER)
                build()
            }


        WorkManager.getInstance(context).enqueueUniqueWork(
            getWorkName(downloadId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            downloadRequest
        )
        return null
    }

    suspend fun cancel(context: Context, downloadId: Int): Boolean {
        val operation = WorkManager.getInstance(context).cancelUniqueWork(getWorkName(downloadId))
        operation.await()
        return true
    }

    fun checkIsDownloading(context: Context, downloadId: Int): Boolean {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(getWorkName(downloadId)).isDone
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_WORKER)
    }

    class Worker(appContext: Context, params: WorkerParameters)
        : CoroutineWorker(appContext, params) {

        private suspend fun download(response: Response, file: File, downloadId: Int,
                                     uploadId: Int) = withContext(Dispatchers.IO) {
            val totalBytes = response.body!!.contentLength()
            var progressPercent: Long = 0

            file.parentFile!!.mkdirs()

            val input = BufferedInputStream(response.body!!.byteStream())
            FileOutputStream(file, false).use { output ->
                val buffer = ByteArray(1024)
                var bytesRead: Long = 0

                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count == -1)
                        break
                    bytesRead += count
                    output.write(buffer, 0, count)

                    val currentProgress: Long = 100 * bytesRead / totalBytes
                    if (currentProgress != progressPercent) {
                        Downloader.onProgress(applicationContext, file, downloadId,
                            uploadId, currentProgress.toInt())
                        progressPercent = currentProgress
                    }
                }
                output.flush()
            }
        }

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val url = inputData.getString(WORKER_URL)!!
            val filePath = inputData.getString(WORKER_FILE_PATH)!!
            val downloadId = inputData.getInt(WORKER_DOWNLOAD_ID, -1)
            val uploadId = inputData.getInt(WORKER_UPLOAD_ID, -1)
            val file = File(filePath)

            try {
                val request = Request.Builder().run {
                    url(url)
                    CookieManager.getInstance()?.getCookie(url)?.let { cookie ->
                        addHeader("Cookie", cookie)
                    }
                    get()
                    build()
                }

                val response = suspendCancellableCoroutine<Response> { cont ->
                    Mitch.httpClient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            cont.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            //TODO: use experimental API for safer close
                            cont.invokeOnCancellation {
                                response.close()
                            }
                            cont.resume(response)
                        }
                    })
                }

                response.use {
                    Downloader.onProgress(applicationContext, file, downloadId,
                        uploadId, null)

                    download(response, file, downloadId, uploadId)

                    with(NotificationManagerCompat.from(applicationContext)) {
                        cancel(NOTIFICATION_TAG_DOWNLOAD, downloadId)
                    }

                    //Add some shitty delay because if you send the completion notification
                    //right after a progress notification, sometimes it doesn't show up
                    delay(500)

                    Downloader.onCompleted(applicationContext, file.name, uploadId, downloadId)
                }
            } catch (e: CancellationException) {
                return@withContext Result.failure()
            } catch (e: Exception) {
                Log.e(LOGGING_TAG, "Caught while downloading", e)
                val errorName = if (e is IOException)
                    R.string.notification_download_io_error
                else
                    R.string.notification_download_unknown_error
                Downloader.onError(applicationContext, file, downloadId, uploadId,
                    e.localizedMessage ?: applicationContext.getString(errorName), e)
                Result.failure()
            }

            Result.success()
        }
    }
}
