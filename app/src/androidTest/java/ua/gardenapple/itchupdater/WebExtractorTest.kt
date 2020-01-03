package ua.gardenapple.itchupdater

import androidx.test.ext.junit.runners.AndroidJUnit4
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
        assertEquals("21 June 2019 @ 08:40", versionInfo.timestamp)

        returnValue = UpdateCheckWebTask().execute(GameStoreInfo("https://terrycavanagh.itch.io/super-hexagon", GameMonetizationType.Paid, "https://terrycavanagh.itch.io/super-hexagon/download/nGM_T_fa5YQ4cMcMFQ4AnSn__H_1Aj670uwLHMiL")).get()
        versionInfo = returnValue.versions[GamePlatform.Android]!![0]

        assertEquals("Super Hexagon [Android]", versionInfo.versionName)
        assertEquals("01 July 2015 @ 01:00", versionInfo.timestamp)
        assertEquals("26 MB", versionInfo.fileSize)
        assertEquals(74588, versionInfo.uploadID)
    }
}
