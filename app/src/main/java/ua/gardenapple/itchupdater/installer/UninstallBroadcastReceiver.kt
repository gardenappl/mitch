package ua.gardenapple.itchupdater.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.database.AppDatabase

class UninstallBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val LOGGING_TAG = "UninstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        if(intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            Log.w(LOGGING_TAG, "Wrong action type!")
        }
        Log.d(LOGGING_TAG, "Data: ${intent.data}")
        Log.d(LOGGING_TAG, "My data: ${intent.data!!.schemeSpecificPart}")
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            var installs = db.installDao.getAllInstallationsSync()
            for(install in installs) {
                Log.d(LOGGING_TAG, install.toString())
            }

            db.installDao.deleteFinishedInstallation(intent.data!!.schemeSpecificPart)

            installs = db.installDao.getAllInstallationsSync()
            for(install in installs) {
                Log.d(LOGGING_TAG, install.toString())
            }
        }
    }
}
