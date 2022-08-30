package garden.appl.mitch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.acra.ACRA

/**
 * Receives broadcast when user taps an update check notification which says "Error"
 */
class ErrorReportBroadcastReciever : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "ErrorBroadcastReceive"

        const val EXTRA_ERROR_STRING = "ERROR_STRING"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.w(LOGGING_TAG, "Received")
        val errorString = intent!!.getStringExtra(EXTRA_ERROR_STRING)!!
        Log.w(LOGGING_TAG, "Reporting error: $errorString")

        ACRA.errorReporter.handleException(Utils.ErrorReport(errorString))
    }
}
