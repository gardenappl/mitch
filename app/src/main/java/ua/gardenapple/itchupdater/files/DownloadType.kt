package ua.gardenapple.itchupdater.files

import ua.gardenapple.itchupdater.install.AbstractInstaller

enum class DownloadType {
    /** Downloading .apk file into a [AbstractInstaller] with [AbstractInstaller.Type.Stream] */
    SESSION_INSTALL,
    /** Downloading .apk file for later installation with [AbstractInstaller.Type.File] */
    FILE_APK,
    /** Downloading generic file */
    FILE
}