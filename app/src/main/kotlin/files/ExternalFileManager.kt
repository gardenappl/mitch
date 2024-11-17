package garden.appl.mitch.files

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import garden.appl.mitch.FILE_PROVIDER
import garden.appl.mitch.Mitch
import garden.appl.mitch.PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT
import garden.appl.mitch.PERMISSION_REQUEST_MOVE_TO_DOWNLOADS
import garden.appl.mitch.PERMISSION_REQUEST_START_DOWNLOAD
import garden.appl.mitch.Utils
import garden.appl.mitch.ui.MitchActivity
import jodd.io.IOUtil
import jodd.net.MimeTypes
import java.io.File

class ExternalFileManager {
    companion object {
        private const val LOGGING_TAG = "ExternalFileManager"
    }

    private var lastUploadId: Int = 0
    private lateinit var moveToDownloadsCallback: (Uri?, String?) -> Unit
    private lateinit var requestPermissionCallback: () -> Unit
    
    private lateinit var lastExternalFileUri: Uri
    private lateinit var getViewIntentCallback: (Intent?) -> Unit

    /**
     * Move a completed download to downloads folder.
     * Will request permission if necessary.
     * @param activity activity which might request permission (must implement [Activity.onRequestPermissionsResult]
     * @param uploadId downloaded file to move
     * @param callback function which receives the new file URI (it should be usable with [getViewIntent]) and a filename for display purposes.
     */
    fun moveToDownloads(activity: MitchActivity, uploadId: Int, callback: (Uri?, String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            || ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED) {
            val (newUri, fileName) = doMoveToDownloads(activity, uploadId)
            callback(newUri, fileName)
        } else {
            lastUploadId = uploadId
            moveToDownloadsCallback = callback
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_MOVE_TO_DOWNLOADS
            )
        }
    }
    
    fun resumeMoveToDownloads(context: Context) {
        val (newUri, fileName) = doMoveToDownloads(context, lastUploadId)
        moveToDownloadsCallback(newUri, fileName)
    }

    private fun doMoveToDownloads(context: Context, uploadId: Int): Pair<Uri?, String?> {
        val file = Mitch.installDownloadManager.getDownloadedFile(uploadId)
        return doMoveToDownloads(context, file)
    }

    /**
     * Move a completed download to downloads folder.
     * Will NOT handle permissions on Android < 29, request permissions beforehand
     */
    fun doMoveToDownloads(context: Context, file: File?): Pair<Uri?, String?> {
        if (file?.exists() != true) {
            return Pair(null, null)
        }
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, MimeTypes.getMimeType(file.extension))
                put(MediaStore.MediaColumns.SIZE, file.length())
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return Pair(null, null)
            resolver.openOutputStream(uri).use { outputStream ->
                file.inputStream().use { inputStream ->
                    IOUtil.copy(inputStream, outputStream)
                }
            }
            return Pair(uri, file.name)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            var attemptBaseName = file.nameWithoutExtension
            val extension = file.extension

            var i = 0
            while (true) {
                try {
                    var newFile = File(downloadsDir, "$attemptBaseName.$extension")
                    if (!file.renameTo(newFile)) {
                        newFile = file.copyTo(newFile)
                        file.delete()
                    }
                    return Pair(
                        Utils.getIntentUriForFile(context, newFile, FILE_PROVIDER),
                        file.name
                    )
                } catch (e: FileAlreadyExistsException) {
                    i++
                    attemptBaseName = "${file.nameWithoutExtension}-$i"
                }
            }
        }
    }

    /**
     * Get intent for viewing an externally saved file
     * @param activity activity which might request permission (must implement [Activity.onRequestPermissionsResult]
     * @param externalFileUriOrName external file name generated by [moveToDownloads]
     * @param callback function which receives the intent. If file no longer exists, can
     */
    fun getViewIntent(activity: Activity, externalFileUri: Uri, callback: (Intent?) -> Unit) {
        Log.d(LOGGING_TAG, "Opening $externalFileUri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED) {
            val intent = getViewIntent(activity, externalFileUri)
            callback(intent)
        } else {
            lastExternalFileUri = externalFileUri
            getViewIntentCallback = callback
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_DOWNLOADS_VIEW_INTENT
            )
        }
    }
    
    fun resumeGetViewIntent(context: Context) {
        val intent = getViewIntent(context, lastExternalFileUri)
        getViewIntentCallback(intent)
    }

    fun getViewIntent(context: Context, externalFileUri: Uri): Intent? {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = externalFileUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return if (intent.resolveActivity(context.packageManager) != null)
            intent
        else
            null
    }

    fun requestPermissionIfNeeded(activity: MitchActivity, callback: () -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED) {
            callback()
        } else {
            requestPermissionCallback = callback
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_START_DOWNLOAD
            )
        }
    }

    fun resumeRequestPermission() {
        requestPermissionCallback()
    }
}