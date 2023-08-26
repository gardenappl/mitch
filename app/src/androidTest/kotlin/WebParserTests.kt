package garden.appl.mitch

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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

    /**
     * This test will only complete successfully if you're logged in to my itch.io account
     */
    @Test
    fun testGetInstallations_paidGame_noVersioning() {
        val gameId = 276085
        val game = Game(gameId, name = "Super Hexagon", author = "Terry Cavanagh",
            downloadPageUrl = "https://terrycavanagh.itch.io/super-hexagon/download/nGM_T_fa5YQ4cMcMFQ4AnSn__H_1Aj670uwLHMiL",
            storeUrl = "https://terrycavanagh.itch.io/super-hexagon",
            thumbnailUrl = "",
            lastUpdatedTimestamp = null
        )

        val doc: Document = runBlocking(Dispatchers.IO) {
            ItchWebsiteUtils.fetchAndParse(game.downloadPageUrl ?: game.storeUrl)
        }

        val installs = ItchWebsiteParser.getInstallations(doc)
        assertEquals(4, installs.size)

        assertEquals("Super Hexagon [Android]", installs[3].uploadName)
        assertEquals("16 December 2021 @ 11:48", installs[3].uploadTimestamp)
        assertEquals("143 MB", installs[3].fileSize)
        assertEquals(4942166, installs[3].uploadId)
        assertEquals(null, installs[3].version)
    }

    @Test
    fun testGetInstallations_donationGame_withVersioning() {
        val gameId = 140169
        val game = Game(gameId, name = "Mindustry", author = "Anuke",
            downloadPageUrl = null,
            storeUrl = "https://anuke.itch.io/mindustry",
            thumbnailUrl = "",
            lastUpdatedTimestamp = null
        )

        val doc: Document = runBlocking(Dispatchers.IO) {
            ItchWebsiteUtils.fetchAndParse(getDownloadPage(game.storeUrl)!!.url)
        }

        val installs = ItchWebsiteParser.getInstallations(doc)

        assertEquals(Installation.PLATFORM_ANDROID, installs[4].platforms)
        assertEquals("[Android]Mindustry.apk", installs[4].uploadName)
        assertEquals(1615327, installs[4].uploadId)
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
        assertEquals(false, url.isStorePage)
        val doc = Jsoup.connect(url.url).get()

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

    suspend fun getDownloadPage(storeUrl: String): ItchWebsiteParser.DownloadUrl? {
        val doc = ItchWebsiteUtils.fetchAndParse(storeUrl)
        return ItchWebsiteParser.getDownloadUrl(doc, storeUrl)
    }
}
