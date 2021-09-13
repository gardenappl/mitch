package ua.gardenapple.itchupdater

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.SingleUpdateChecker
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game

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
                db.addMitchToDatabase(context)
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

    @Test(expected = IllegalArgumentException::class)
    fun testUpdateCheck_mitch_fdroid() {
        Assume.assumeTrue(BuildConfig.FLAVOR == FLAVOR_FDROID)

        runBlocking(Dispatchers.IO) {
            val game = db.gameDao.getGameById(Game.MITCH_GAME_ID)!!
            val install = db.installDao.getFinishedInstallationsForGame(Game.MITCH_GAME_ID)[0]
            val (updateCheckDoc, downloadUrlInfo) = updateChecker.getDownloadInfo(game)!!

            //should crash
            updateChecker.checkUpdates(game, install, updateCheckDoc, downloadUrlInfo)
        }
    }
}