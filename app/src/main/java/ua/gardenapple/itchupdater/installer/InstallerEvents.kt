package ua.gardenapple.itchupdater.installer

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

interface DownloadCompleteListener {
    suspend fun onDownloadComplete(downloadId: Int, isInstallable: Boolean)
}

interface DownloadFailListener {
    suspend fun onDownloadFailed(downloadId: Int)
}

interface InstallResultListener {
    suspend fun onInstallResult(installSessionId: Int, packageName: String, apkName: String?, status: Int)
}

interface InstallStartListener {
    suspend fun onInstallStart(downloadId: Int, pendingInstallSessionId: Int)
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

        suspend fun notifyApkInstallStart(downloadId: Int, pendingInstallSessionId: Int) {
            coroutineScope {
                for (listener in installStartListeners) {
                    launch {
                        listener.onInstallStart(downloadId, pendingInstallSessionId)
                    }
                }
            }
        }

        suspend fun notifyDownloadComplete(downloadId: Int, isInstallable: Boolean) {
            coroutineScope {
                for (listener in downloadCompleteListeners) {
                    launch {
                        listener.onDownloadComplete(downloadId, isInstallable)
                    }
                }
            }
        }

        suspend fun notifyDownloadFailed(downloadId: Int) {
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