package garden.appl.mitch.files

import garden.appl.mitch.install.AbstractInstaller

enum class DownloadType {
    /** Installing an .apk file with [AbstractInstaller.Type.Stream] */
    INSTALL_SESSION,
    /** Downloading .apk file for later installation with [AbstractInstaller.Type.File] */
    INSTALL_APK,
    /** "Installing" non-apk game content */
    INSTALL_MISC,
    /** Downloading a file without "installing" it. For example, exporting save data from a web game */
    NORMAL_FILE
}