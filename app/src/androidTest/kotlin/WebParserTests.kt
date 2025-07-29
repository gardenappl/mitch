package garden.appl.mitch

import androidx.test.ext.junit.runners.AndroidJUnit4
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.equalTo
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebParserTests {
    @Test
    fun testGetDownloadUrl_paidGame() {
        val storeUrl = "https://npckc.itch.io/a-tavern-for-tea"
        val storeDoc = runBlocking { ItchWebsiteUtils.fetchAndParse(storeUrl) }
        assumeThat(ItchWebsiteUtils.getLoggedInUserName(storeDoc), equalTo("gardenapple"))

        val downloadUrl = runBlocking {
            ItchWebsiteParser.getOrFetchDownloadUrl(storeDoc, storeUrl)
        }
        assertEquals(ItchWebsiteParser.DownloadUrl(
            "https://npckc.itch.io/a-tavern-for-tea/download/VcTYvLj_mPzph_hcLK5fuMafmTlH11SPBlJhfoRh",
            isPermanent = true,
            isStorePage = false
        ), downloadUrl)
    }

    @Test
    fun testGetDownloadUrl_freeGame() {
        val storeUrl = "https://clouddeluna.itch.io/splashyplusplus"
        val url = runBlocking {
            ItchWebsiteParser.getOrFetchDownloadUrl(ItchWebsiteUtils.fetchAndParse(storeUrl), storeUrl)
        }
        assertEquals(ItchWebsiteParser.DownloadUrl(
            "https://clouddeluna.itch.io/splashyplusplus",
            isPermanent = true,
            isStorePage = true
        ), url)
    }

    @Test
    fun testGetDownloadUrl_donationGame() {
        val storeUrl = "https://anuke.itch.io/mindustry"
        val url = runBlocking {
            ItchWebsiteParser.getOrFetchDownloadUrl(ItchWebsiteUtils.fetchAndParse(storeUrl), storeUrl)
        }
        assertNotNull(url)
        assertEquals(false, url!!.isPermanent)
        assertEquals(false, url.isStorePage)
        val doc = Jsoup.connect(url.url).get()

        assertEquals(true, doc.getElementsByClass("download_btn").isNotEmpty())
    }

    @Test
    fun testGetDownloadUrl_inaccessibleGame() {
        val storeUrl = "https://sukebangames.itch.io/valhalla-bar"
        val url = runBlocking {
            ItchWebsiteParser.getOrFetchDownloadUrl(ItchWebsiteUtils.fetchAndParse(storeUrl), storeUrl)
        }
        assertNull(url)
    }

    @Test
    fun testGetInstallations_paidGame_noVersioning() {
        val gameId = 276085
        val game = Game(gameId, name = "Super Hexagon", author = "Terry Cavanagh",
            downloadPageUrl = "https://terrycavanagh.itch.io/super-hexagon/download/nGM_T_fa5YQ4cMcMFQ4AnSn__H_1Aj670uwLHMiL",
            storeUrl = "https://terrycavanagh.itch.io/super-hexagon",
            thumbnailUrl = "",
            lastUpdatedTimestamp = null
        )
        val storeDoc = runBlocking { ItchWebsiteUtils.fetchAndParse(game.storeUrl) }
        assumeThat(ItchWebsiteUtils.getLoggedInUserName(storeDoc), equalTo("gardenapple"))

        val downloadPageDoc = runBlocking {
            ItchWebsiteUtils.fetchAndParse(game.downloadPageUrl!!)
        }

        val installs = ItchWebsiteParser.getInstallations(downloadPageDoc)
        assertEquals(4, installs.size)

        assertEquals("Super Hexagon [Android]", installs[3].uploadName)
        assertEquals("07 November 2022 @ 16:44 UTC", installs[3].uploadTimestamp)
        assertEquals("144 MB", installs[3].fileSize)
        assertEquals(6794483, installs[3].uploadId)
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
        val downloadDoc = runBlocking {
            val storeDoc = ItchWebsiteUtils.fetchAndParse(game.storeUrl)
            val downloadUrl = ItchWebsiteParser.getOrFetchDownloadUrl(storeDoc, game.storeUrl)
            ItchWebsiteUtils.fetchAndParse(downloadUrl!!.url)
        }
        val installs = ItchWebsiteParser.getInstallations(downloadDoc)

        assertEquals(Installation.PLATFORM_ANDROID, installs[4].platforms)
        assertEquals("[Android]Mindustry.apk", installs[4].uploadName)
        assertEquals(1615327, installs[4].uploadId)
    }
}
