package ua.gardenapple.itchupdater

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ua.gardenapple.itchupdater.client.WebUpdateChecker
import ua.gardenapple.itchupdater.database.game.Game

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class WebUpdaterTest {
    companion object {
        const val LOGGING_TAG: String = "Test"
    }

    private val webChecker = WebUpdateChecker()

    /*
     * This test will only complete successfully if you're logged in to my itch.io account
     */
    @Test
    fun testUpdateCheck_paidGame_noVersioning() {
        val game = Game(276085, name = "Super Hexagon", author = "Terry Cavanagh",
            downloadPageUrl = "https://terrycavanagh.itch.io/super-hexagon/download/nGM_T_fa5YQ4cMcMFQ4AnSn__H_1Aj670uwLHMiL",
            storeUrl = "https://terrycavanagh.itch.io/super-hexagon",
            thumbnailUrl = "")

        val doc: Document = runBlocking(Dispatchers.IO) {
            webChecker.fetchDownloadPage(game)
        }

        Log.d(LOGGING_TAG, "HTML: ")
        Utils.logLongD(LOGGING_TAG, doc.outerHtml())

        val uploads = webChecker.getAndroidUploads(276085, doc)

        assertEquals(1, uploads.size)
        assertEquals("Super Hexagon [Android]", uploads[0].name)
        assertEquals("01 July 2015 @ 01:00", uploads[0].uploadTimestamp)
        assertEquals("26 MB", uploads[0].fileSize)
        assertEquals(74588, uploads[0].uploadId)
        assertEquals(null, uploads[0].version)
    }
}
