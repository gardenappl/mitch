package garden.appl.mitch.files

import android.content.Context
import android.os.StatFs
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import garden.appl.mitch.Mitch
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD_LONG
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.install.AbstractInstaller
import garden.appl.mitch.install.InstallationDownloadFileListener
import garden.appl.mitch.install.Installations
import garden.appl.mitch.install.SessionInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Downloader {
    private const val WORKER_URL = "url"
    private const val WORKER_DOWNLOAD_DIR = "path"
    private const val WORKER_FILE_NAME = "file_name"
    private const val WORKER_DOWNLOAD_OR_INSTALL_ID = "download_id"
    private const val WORKER_UPLOAD_ID = "upload_id"
    private const val WORKER_CONTENT_LENGTH = "content_length"
    private const val TAG_WORKER = "MITCH_DOWN"

    private const val LOGGING_TAG = "WorkerDownloader"
    private val installationListener = InstallationDownloadFileListener()
    private val normalListener = DownloadFileListener()

    private fun getListener(type: DownloadType): DownloadFileListener {
        return when (type) {
            DownloadType.NORMAL_FILE -> normalListener
            else -> installationListener
        }
    }

    fun getNormalDownloadPath(context: Context, downloadId: Long) =
        File(File(context.filesDir, "misc_download"), downloadId.toString())

    @Synchronized
    private fun getUnusedDownloadId(context: Context): Long {
        for (i in Int.MAX_VALUE.toLong() + 1..Int.MAX_VALUE.toLong() * 2) {
            val workInfos =
                WorkManager.getInstance(context).getWorkInfosForUniqueWork(getWorkName(i))
                    .get()
            if (workInfos.isEmpty())
                return i
        }
        throw RuntimeException("Could not find free download ID for DownloaderWorker")
    }

    private fun getWorkName(downloadId: Long): String = "MITCH_DOWN_$downloadId"

    /**
     * @param contentLength file size, null if unknown
     * @param installer null if we are downloading into a file
     * @param downloadDir null if [installer] has type [AbstractInstaller.Type.Stream] or if [tempDownloadDir]
     */
    suspend fun requestDownload(
        context: Context,
        url: String,
        install: Installation?,
        fileName: String,
        contentLength: Long?,
        downloadDir: File?,
        tempDownloadDir: Boolean,
        installer: AbstractInstaller?
    ) {
        val id = if (installer != null)
            installer.createSessionForStreamInstall(context).toLong()
        else
            getUnusedDownloadId(context)
        val downloadDir = if (tempDownloadDir)
            getNormalDownloadPath(context, id)
        else
            downloadDir

        if (install != null) {
            Log.d(LOGGING_TAG, "Download or stream install ID: $id")
            install.downloadOrInstallId = id
            install.status = Installation.STATUS_DOWNLOADING

            val db = AppDatabase.getDatabase(context)
            db.installDao.upsert(install)
        }

        val downloadRequest =
            OneTimeWorkRequestBuilder<Worker>().run {
                setInputData(
                    workDataOf(
                        Pair(WORKER_URL, url),
                        Pair(WORKER_CONTENT_LENGTH, contentLength),
                        Pair(WORKER_DOWNLOAD_DIR, downloadDir?.path),
                        Pair(WORKER_FILE_NAME, fileName),
                        Pair(WORKER_DOWNLOAD_OR_INSTALL_ID, id),
                        Pair(WORKER_UPLOAD_ID, install?.uploadId)
                    )
                )
                addTag(TAG_WORKER)
                build()
            }


        WorkManager.getInstance(context).enqueueUniqueWork(
            getWorkName(id),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            downloadRequest
        )
    }

    suspend fun cancel(context: Context, downloadId: Long): Boolean {
        val operation = WorkManager.getInstance(context).cancelUniqueWork(getWorkName(downloadId))
        operation.await()
        return true
    }

    fun checkIsDownloading(context: Context, downloadId: Long): Boolean {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(getWorkName(downloadId)).isDone
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_WORKER)
    }

    class Worker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val url = inputData.getString(WORKER_URL)!!
            val downloadDir = inputData.getString(WORKER_DOWNLOAD_DIR)
            val fileName = inputData.getString(WORKER_FILE_NAME)!!
            val contentLength = inputData.getLong(WORKER_CONTENT_LENGTH, -1)
            val downloadOrInstallId = inputData.getLong(WORKER_DOWNLOAD_OR_INSTALL_ID, -1)
            val uploadId = Utils.getInt(inputData, WORKER_UPLOAD_ID)

            val downloadType = if (downloadDir == null)
                DownloadType.INSTALL_SESSION
            else if (uploadId == null)
                DownloadType.NORMAL_FILE
            else if (fileName.endsWith(".apk"))
                DownloadType.INSTALL_APK
            else
                DownloadType.INSTALL_MISC
            Log.d(LOGGING_TAG, "Download type: $downloadType")
            val listener = getListener(downloadType)

            try {
                Log.d(LOGGING_TAG, "content length: $contentLength")
                if (downloadDir != null) {
                    File("${downloadDir}/").mkdirs()

                    if (StatFs(downloadDir).availableBytes <= contentLength)
                        throw SessionInstaller.NotEnoughSpaceException()
                }

                val outputStream = if (downloadDir != null) {
                    val file = File(downloadDir, fileName)

                    FileOutputStream(file, false)
                } else {
                    val installer = Installations.getInstaller(downloadOrInstallId)

                    installer.openWriteStream(
                        applicationContext,
                        downloadOrInstallId.toInt(),
                        contentLength
                    )
                }

                if (DataURL.isValid(url)) {
                    listener.onProgress(applicationContext, fileName, downloadOrInstallId, null)
                    Utils.cancellableCopy(DataURL(url).toInputStream(), outputStream)
                    listener.onCompleted(applicationContext, fileName, uploadId, downloadOrInstallId, downloadType)

                    return@withContext Result.success()
                }

                val request = Request.Builder().run {
                    url(url)
                    CookieManager.getInstance()?.getCookie(url)?.let { cookie ->
                        addHeader("Cookie", cookie)
                    }
                    get()
                    build()
                }

                val response = suspendCancellableCoroutine { cont ->
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
                    listener.onProgress(applicationContext, fileName, downloadOrInstallId, null)

                    outputStream.use {
                        download(response, it, fileName, downloadOrInstallId, listener)
                    }

                    with(NotificationManagerCompat.from(applicationContext)) {
                        if (Utils.fitsInInt(downloadOrInstallId))
                            cancel(NOTIFICATION_TAG_DOWNLOAD, downloadOrInstallId.toInt())
                        else
                            cancel(NOTIFICATION_TAG_DOWNLOAD_LONG, downloadOrInstallId.toInt())
                    }

                    //Add some shitty delay because if you send the completion notification
                    //right after a progress notification, sometimes it doesn't show up
                    delay(500)

                    listener.onCompleted(applicationContext,
                            fileName, uploadId, downloadOrInstallId, downloadType)
                }
            } catch (e: CancellationException) {
                listener.onCancel(applicationContext, downloadOrInstallId)
                Result.failure()
            } catch (e: Exception) {
                Log.e(LOGGING_TAG, "Caught while downloading", e)
                val errorName = when (e) {
                    is SessionInstaller.NotEnoughSpaceException ->
                        if (fileName.endsWith(".apk"))
                            R.string.dialog_installer_no_space
                        else
                            R.string.notification_download_no_space
                    is IOException -> R.string.notification_download_io_error
                    else -> R.string.notification_download_unknown_error
                }
                listener.onError(
                    applicationContext, fileName, uploadId, downloadOrInstallId, downloadType,
                    e.localizedMessage ?: applicationContext.getString(errorName), e
                )
                Result.failure()
            }

            Result.success()
        }

        private suspend fun download(
            response: Response, outputStream: OutputStream, fileName: String,
            downloadId: Long, listener: DownloadFileListener
        ) = withContext(Dispatchers.IO) {
            val totalBytes = response.body.contentLength()
            var progressPercent: Long = 0

            val body = response.body

            BufferedInputStream(body.byteStream()).use { inputStream ->
                Utils.cancellableCopy(inputStream, outputStream) { bytesRead ->
                    val currentProgress: Long = 100 * bytesRead / totalBytes
                    if (currentProgress != progressPercent) {
                        listener.onProgress(applicationContext,
                                fileName, downloadId, currentProgress.toInt())
                        progressPercent = currentProgress
                    }
                }
            }
        }
    }
}
