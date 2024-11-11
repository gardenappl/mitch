package garden.appl.mitch.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import garden.appl.mitch.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class UninstallBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "UninstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED)
            throw RuntimeException("Wrong action type!")

        Log.d(LOGGING_TAG, "Data: ${intent.data}")

        val packageName = intent.data!!.schemeSpecificPart
        Log.d(LOGGING_TAG, "Package name: $packageName")

        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.installDao.deleteFinishedInstallation(packageName)
        }
    }
}
