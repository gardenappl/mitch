package ua.gardenapple.itchupdater

import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ua.gardenapple.itchupdater.client.web.UpdateCheckWebTask

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class WebExtractorTest {
    @Test
    fun testUpdateCheck() {
        var returnValue = UpdateCheckWebTask().execute(GameStoreInfo("https://clouddeluna.itch.io/splashyplusplus", GameMonetizationType.Free)).get()
        var versionInfo = returnValue.versions[GamePlatform.Android]!![0]

        assertEquals("splashy++.apk", versionInfo.versionName)
        assertEquals("17 April 2019 @ 10:07", versionInfo.timestamp)

        returnValue = UpdateCheckWebTask().execute(GameStoreInfo("https://terrycavanagh.itch.io/dicey-dungeons", GameMonetizationType.Paid, "https://terrycavanagh.itch.io/dicey-dungeons/download/YwMl_S3BdGvDZ9nW7wYXO9H7Fl_ydrbo3Tgw0c91")).get()
        versionInfo = returnValue.versions[GamePlatform.Android]!![0]

        assertEquals("Dicey Dungeons v0.16.3 Windows", versionInfo.versionName)
        assertEquals("06 March 2019 @ 18:09", versionInfo.timestamp)
        assertEquals("475 MB", versionInfo.fileSize)
        assertEquals(1219878, versionInfo.uploadID)
    }
}
