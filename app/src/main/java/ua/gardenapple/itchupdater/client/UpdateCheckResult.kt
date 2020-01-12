package ua.gardenapple.itchupdater.client

enum class UpdateCheckResult {
    UP_TO_DATE,
    UNKNOWN,
    ACCESS_DENIED,
    POSSIBLE_UPDATE_AVAILABLE,
    UPDATE_AVAILABLE,
    MULTIPLE_UPDATES
}