package ua.gardenapple.itchupdater

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.gardenapple.itchupdater.client.ItchWebsiteParser
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.client.WebUpdateChecker
import ua.gardenapple.itchupdater.database.game.Game

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class WebParserTests {
    companion object {
        const val LOGGING_TAG: String = "Test"
    }

    private lateinit var updateChecker: WebUpdateChecker

    @Before
    fun setup() {
        updateChecker = WebUpdateChecker(InstrumentationRegistry.getInstrumentation().targetContext)
    }


    /**
     * This test will only complete successfully if you're logged in to my itch.io account
     */
    @Test
    fun testGetAndroidUploads_paidGame_noVersioning() {
        val gameId = 276085
        val game = Game(gameId, name = "Super Hexagon", author = "Terry Cavanagh",
            downloadPageUrl = "https://terrycavanagh.itch.io/super-hexagon/download/nGM_T_fa5YQ4cMcMFQ4AnSn__H_1Aj670uwLHMiL",
            storeUrl = "https://terrycavanagh.itch.io/super-hexagon",
            thumbnailUrl = "",
            lastDownloadTimestamp = null
        )

        val doc: Document = runBlocking(Dispatchers.IO) {
            ItchWebsiteUtils.fetchAndParseDocument(game.downloadPageUrl ?: game.storeUrl)
        }

//        Log.d(LOGGING_TAG, "HTML: ")
//        Utils.logLongD(LOGGING_TAG, doc.outerHtml())

        val uploads = ItchWebsiteParser.getAndroidUploads(gameId, doc)

        assertEquals(1, uploads.size)
        assertEquals("Super Hexagon [Android]", uploads[0].name)
        assertEquals("01 July 2015 @ 01:00", uploads[0].uploadTimestamp)
        assertEquals("26 MB", uploads[0].fileSize)
        assertEquals(74588, uploads[0].uploadId)
        assertEquals(null, uploads[0].version)
    }

    @Test
    fun testGetAndroidUploads_donationGame_withVersioning() {
        val gameId = 140169
        val game = Game(gameId, name = "Mindustry", author = "Anuke",
            downloadPageUrl = null,
            storeUrl = "https://anuke.itch.io/mindustry",
            thumbnailUrl = "",
            lastDownloadTimestamp = null
        )

        val doc: Document = runBlocking(Dispatchers.IO) {
            ItchWebsiteUtils.fetchAndParseDocument(game.storeUrl)
        }

//        Log.d(LOGGING_TAG, "HTML: ")
//        Utils.logLongD(LOGGING_TAG, doc.outerHtml())

        val uploads = ItchWebsiteParser.getAndroidUploads(gameId, doc)

        assertEquals(1, uploads.size)
        assertEquals("[Android]Mindustry.apk", uploads[0].name)
        assertEquals(null, uploads[0].uploadTimestamp)
        assertEquals("34 MB", uploads[0].fileSize)
        assertEquals(null, uploads[0].uploadId)
        assertEquals("Версия 102.3", uploads[0].version)
    }

    /**
     * This test will only complete successfully if you're logged in to my itch.io account
     */
    @Test
    fun testGetDownloadPage_paidGame() {
        val url: ItchWebsiteParser.DownloadUrl? = runBlocking(Dispatchers.IO) {
            getDownloadPage("https://npckc.itch.io/a-tavern-for-tea")
        }
        assertEquals(ItchWebsiteParser.DownloadUrl("https://npckc.itch.io/a-tavern-for-tea/download/VcTYvLj_mPzph_hcLK5fuMafmTlH11SPBlJhfoRh", true, false), url)
    }

    @Test
    fun testGetDownloadPage_freeGame() {
        val url: ItchWebsiteParser.DownloadUrl? = runBlocking(Dispatchers.IO) {
            getDownloadPage("https://clouddeluna.itch.io/splashyplusplus")
        }
        assertEquals(ItchWebsiteParser.DownloadUrl("https://clouddeluna.itch.io/splashyplusplus", true, true), url)
    }

    @Test
    fun testGetDownloadPage_donationGame() {
        val url: ItchWebsiteParser.DownloadUrl? = runBlocking(Dispatchers.IO) {
            getDownloadPage("https://anuke.itch.io/mindustry")
        }
        assertNotNull(url)
        Log.d(LOGGING_TAG, url!!.url)
        assertEquals(false, url.isPermanent)
        val doc = Jsoup.connect(url.url).get()

//        Log.d(LOGGING_TAG, "HTML: ")
//        Utils.logLongD(LOGGING_TAG, doc.outerHtml())

        assertEquals(true, doc.getElementsByClass("download_btn").isNotEmpty())
    }

    /**
     * This test will only complete successfully if you're not logged in to an account that owns VA-11 HALL-A
     */
    @Test
    fun testGetDownloadPage_inaccessibleGame() {
        val url: ItchWebsiteParser.DownloadUrl? = runBlocking(Dispatchers.IO) {
            getDownloadPage("https://sukebangames.itch.io/valhalla-bar")
        }
        assertNull(url)
    }

    suspend fun getDownloadPage(url: String): ItchWebsiteParser.DownloadUrl? {
        val doc = ItchWebsiteUtils.fetchAndParseDocument(url)
        return ItchWebsiteParser.getDownloadUrlFromStorePage(doc, url, true)
    }


    @Test
    fun testUpdateCheck_mitch() {
        val result: UpdateCheckResult = runBlocking(Dispatchers.IO) {
            updateChecker.checkUpdates(Game.MITCH_GAME_ID)
        }
        assertEquals(UpdateCheckResult.UP_TO_DATE, result.code)
    }
}
