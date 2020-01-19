package ua.gardenapple.itchupdater.installer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

interface DownloadStartListener {
    suspend fun onDownloadStarted(downloadId: Long)
}

interface DownloadCompleteListener {
    suspend fun onDownloadComplete(downloadId: Long, installPending: Boolean)
}

interface InstallCompleteListener {
    suspend fun onInstallComplete(apkName: String)
}

class InstallerEvents {
    companion object {
        private val installCompleteListeners = ArrayList<InstallCompleteListener>()
        private val downloadCompleteListeners = ArrayList<DownloadCompleteListener>()
        private val downloadStartListeners = ArrayList<DownloadStartListener>()

        fun notifyInstallComplete(apkName: String) {
            for (listener in installCompleteListeners) {
                GlobalScope.launch {
                    listener.onInstallComplete(apkName)
                }
            }
        }

        fun notifyDownloadComplete(downloadId: Long, installPending: Boolean) {
            for(listener in downloadCompleteListeners) {
                GlobalScope.launch {
                    listener.onDownloadComplete(downloadId, installPending)
                }
            }
        }

        fun notifyDownloadStart(downloadId: Long) {
            for(listener in downloadStartListeners) {
                GlobalScope.launch {
                    listener.onDownloadStarted(downloadId)
                }
            }
        }

        fun addListener(listener: InstallCompleteListener) = installCompleteListeners.add(listener)
        fun addListener(listener: DownloadCompleteListener) = downloadCompleteListeners.add(listener)
        fun addListener(listener: DownloadStartListener) = downloadStartListeners.add(listener)

        fun setup() {
            installCompleteListeners.clear()
            downloadCompleteListeners.clear()
            downloadStartListeners.clear()
        }
    }
}