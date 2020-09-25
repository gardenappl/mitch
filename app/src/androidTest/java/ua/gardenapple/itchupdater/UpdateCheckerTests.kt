package ua.gardenapple.itchupdater

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.UpdateChecker
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTests {
    companion object {
        const val LOGGING_TAG: String = "Test"

        private lateinit var updateChecker: UpdateChecker

        @BeforeClass
        @JvmStatic fun setup() {
            val context = InstrumentationRegistry.getInstrumentation().context
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

            db.addMitchToDatabase(context)

            updateChecker = UpdateChecker(db)
        }
    }



    @Test
    fun testUpdateCheck_mitch() {
        val result: UpdateCheckResult = runBlocking(Dispatchers.IO) {
            updateChecker.checkUpdates(Game.MITCH_GAME_ID)
        }
        Assert.assertEquals(UpdateCheckResult.UP_TO_DATE, result.code)
    }
}