package garden.appl.mitch

import junit.framework.TestCase

class UtilsTest : TestCase() {
    fun testIsVersionNewer() {
        assertTrue(Utils.isVersionNewer("Version v2.0.1", "Версия 2.0.0"))
        assertTrue(Utils.isVersionNewer("2.0.1", "2.0.0"))
        assertTrue(Utils.isVersionNewer("2.0.0.1", "2.0.0"))
        assertTrue(Utils.isVersionNewer("2.1.0", "2.0.0"))
        assertTrue(Utils.isVersionNewer("2.0.0", "1.0.9"))
        assertTrue(Utils.isVersionNewer("1.0.010", "1.0.0"))
        assertTrue(Utils.isVersionNewer("2.0", "1.0.15"))

        assertFalse(Utils.isVersionNewer("2.0.0", "2.0.0"))
        assertFalse(Utils.isVersionNewer("Version v1.4.5", "1.4.5"))
        assertFalse(Utils.isVersionNewer("Версия 2.0.0", "Version v2.0.0"))
        assertFalse(Utils.isVersionNewer("2.0.0", "2.0.1"))
        assertFalse(Utils.isVersionNewer("2.0.0", "2.0.0.1"))
        assertFalse(Utils.isVersionNewer("2.0.0", "2.1.0"))
        assertFalse(Utils.isVersionNewer("1.0.9", "2.0.0"))
        assertFalse(Utils.isVersionNewer("2.0.0", "2.0.010"))
        assertFalse(Utils.isVersionNewer("1.0.15", "2.0"))
        assertNull(Utils.isVersionNewer("Nonsense", "Other nonsense"))
    }
}