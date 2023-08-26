package garden.appl.mitch

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import garden.appl.mitch.client.SingleUpdateChecker
import garden.appl.mitch.client.UpdateCheckResult
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTests {
    companion object {
        private lateinit var updateChecker: SingleUpdateChecker
        private lateinit var db: AppDatabase

        @BeforeClass
        @JvmStatic fun setup() {
            val context = InstrumentationRegistry.getInstrumentation().context
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            runBlocking(Dispatchers.IO) {
                db.addMitchToDatabaseIfNeeded(context)
            }

            updateChecker = SingleUpdateChecker(db)
        }
    }



    @Test
    fun testUpdateCheck_mitch_itchio() {
        Assume.assumeTrue(BuildConfig.FLAVOR == FLAVOR_ITCHIO)

        val result: UpdateCheckResult = runBlocking(Dispatchers.IO) {
            val game = db.gameDao.getGameById(Game.MITCH_GAME_ID)!!
            val install = db.installDao.getFinishedInstallationsForGame(Game.MITCH_GAME_ID)[0]
            val (updateCheckDoc, downloadUrlInfo) = updateChecker.getDownloadInfo(game)!!

            updateChecker.checkUpdates(game, install, updateCheckDoc, downloadUrlInfo)
        }
        Assert.assertEquals(UpdateCheckResult.UP_TO_DATE, result.code)
    }
}