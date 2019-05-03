package ua.gardenapple.itchupdater

data class GameStoreInfo (val storeUrl: String,
                          val monetizationType: GameMonetizationType,
                          val downloadUrl: String? = null)

data class GameVersionInfo (val versions: HashMap<GamePlatform, ArrayList<GameVersion>>)

/**
 * timestampBuildSpecific indicates that the timestamp applies specifically to the
 * Android build of a game, not the game as a whole. If it's set to false, that means it's not a
 * reliable indicator that the Android build has updated.
 */
data class GameVersion (val versionName: String,
                        val timestamp: String,
                        val timestampBuildSpecific: Boolean,
                        val fileSize: String,
                        val uploadID: Int = -1) {
    companion object {
        @JvmField
        val Web = GameVersion("web", "", true, "")

        //Returns null if uncertain
        fun isUpdateRequired(version1: GameVersion, version2: GameVersion) : Boolean? {
            if(version1.uploadID != -1 && version2.uploadID != -1)
                return version1.uploadID != version2.uploadID
            if(version1.timestampBuildSpecific && version2.timestampBuildSpecific)
                return version1.timestamp != version2.timestamp

            if(version1.versionName != version2.versionName)
                return true
            if(version1.fileSize != version2.fileSize)
                return true

            return null
        }
    }
}

//Influences the download page URL
enum class GameMonetizationType {
    Paid, DonationBased, Free
}

//Other platforms are not supported
enum class GamePlatform {
    Android, Web
}