package ua.gardenapple.itchupdater.installer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ua.gardenapple.itchupdater.database.game.Game

interface DownloadStartListener {
    suspend fun onDownloadStarted(downloadId: Long)
}

interface DownloadCompleteListener {
    suspend fun onDownloadComplete(downloadId: Long, pendingInstallId: Int?)
}

interface InstallCompleteListener {
    suspend fun onInstallComplete(installSessionId: Int, packageName: String, game: Game, status: Int)
}

class InstallerEvents {
    companion object {
        private val installCompleteListeners = ArrayList<InstallCompleteListener>()
        private val downloadCompleteListeners = ArrayList<DownloadCompleteListener>()
        private val downloadStartListeners = ArrayList<DownloadStartListener>()

        suspend fun notifyApkInstallComplete(installSessionId: Int, packageName: String, game: Game, status: Int) {
            coroutineScope {
                for (listener in installCompleteListeners) {
                    launch {
                        listener.onInstallComplete(installSessionId, packageName, game, status)
                    }
                }
            }
        }

        fun notifyDownloadComplete(downloadId: Long, pendingInstallSessionId: Int?) {
            for(listener in downloadCompleteListeners) {
                GlobalScope.launch {
                    listener.onDownloadComplete(downloadId, pendingInstallSessionId)
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