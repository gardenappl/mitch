package ua.gardenapple.itchupdater.installer

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

interface DownloadCompleteListener {
    suspend fun onDownloadComplete(downloadId: Long, isInstallable: Boolean)
}

interface DownloadFailListener {
    suspend fun onDownloadFailed(downloadId: Long)
}

interface InstallResultListener {
    suspend fun onInstallResult(installSessionId: Int, packageName: String, apkName: String?, status: Int)
}

interface InstallStartListener {
    suspend fun onInstallStart(downloadId: Long, pendingInstallSessionId: Int)
}

class InstallerEvents {
    companion object {
        private val downloadCompleteListeners = ArrayList<DownloadCompleteListener>()
        private val downloadFailListeners = ArrayList<DownloadFailListener>()
        private val installResultListeners = ArrayList<InstallResultListener>()
        private val installStartListeners = ArrayList<InstallStartListener>()

        suspend fun notifyApkInstallResult(installSessionId: Int, packageName: String, apkName: String?, status: Int) {
            coroutineScope {
                for (listener in installResultListeners) {
                    launch {
                        listener.onInstallResult(installSessionId, packageName, apkName, status)
                    }
                }
            }
        }

        suspend fun notifyApkInstallStart(downloadId: Long, pendingInstallSessionId: Int) {
            coroutineScope {
                for (listener in installStartListeners) {
                    launch {
                        listener.onInstallStart(downloadId, pendingInstallSessionId)
                    }
                }
            }
        }

        suspend fun notifyDownloadComplete(downloadId: Long, isInstallable: Boolean) {
            coroutineScope {
                for (listener in downloadCompleteListeners) {
                    launch {
                        listener.onDownloadComplete(downloadId, isInstallable)
                    }
                }
            }
        }

        suspend fun notifyDownloadFailed(downloadId: Long) {
            coroutineScope {
                for (listener in downloadFailListeners) {
                    launch {
                        listener.onDownloadFailed(downloadId)
                    }
                }
            }
        }

        fun addListener(listener: InstallStartListener) = installStartListeners.add(listener)
        fun addListener(listener: InstallResultListener) = installResultListeners.add(listener)
        fun addListener(listener: DownloadFailListener) = downloadFailListeners.add(listener)
        fun addListener(listener: DownloadCompleteListener) = downloadCompleteListeners.add(listener)

        fun setup() {
            installStartListeners.clear()
            installResultListeners.clear()
            downloadFailListeners.clear()
            downloadCompleteListeners.clear()
        }
    }
}