package garden.appl.mitch.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.installation.Installation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
            val mitchInstall = db.installDao.getPendingInstallation(Installation.MITCH_UPLOAD_ID)
            if (mitchInstall != null)
                db.installDao.delete(mitchInstall)
        }
    }
}