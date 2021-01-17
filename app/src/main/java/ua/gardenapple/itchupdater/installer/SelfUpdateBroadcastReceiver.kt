package ua.gardenapple.itchupdater.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.installation.Installation

class SelfUpdateBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val LOGGING_TAG = "SelfUpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGGING_TAG, "onReceive")
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED)
            throw RuntimeException("Wrong action type!")

        Log.d(LOGGING_TAG, "Mitch updated!")
            
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val mitchPendingInstalls =
                db.installDao.getPendingInstallations(Installation.MITCH_UPLOAD_ID)

            db.installDao.delete(mitchPendingInstalls.filter { install ->
                install.status == Installation.STATUS_INSTALLING
            })
        }
    }
}