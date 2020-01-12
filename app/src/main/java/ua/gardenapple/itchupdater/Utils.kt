package ua.gardenapple.itchupdater

import android.util.Log
import kotlin.math.min

class Utils {
    companion object {
        const val LOG_LIMIT: Int = 1000

        /**
         * Logcat normally has a limit of 1000 characters.
         * This function splits long strings into multiple log entries.
         */
        fun logPrintLong(priority: Int, tag: String, string: String) {
            for (i in string.indices step LOG_LIMIT) {
                Log.d(tag, string.substring(i, min(string.length, i + LOG_LIMIT)))
            }
        }

        /**
         * logPrintLong with Debug priority
         */
        fun logLongD(tag: String, string: String) {
            logPrintLong(Log.DEBUG, tag, string)
        }
    }
}