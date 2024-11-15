package garden.appl.mitch.files

import junit.framework.TestCase
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assume.assumeThat

class DataURLTest : TestCase() {
    fun testToInputStream() {
        val url = """data:,Hello%2C%20World%21"""
        val decoded = DataURL(url).toInputStream().readAllBytes().decodeToString()
        assumeThat(decoded, equalTo("Hello, World!"))
    }

    fun testToInputStream_base64() {
        val url = """data:text/plain;base64,SGVsbG8sIFdvcmxkIQ=="""
        val decoded = DataURL(url).toInputStream().readAllBytes().decodeToString()
        assumeThat(decoded, equalTo("Hello, World!"))
    }

    fun testToInputStream_utf8() {
        val url = """data:text/plain;charset=utf-8,%D0%9F%D1%80%D0%B8%D0%B2%D1%96%D1%82%2C%20%D1%81%D0%B2%D1%96%D1%82%21"""
        val decoded = DataURL(url).toInputStream().readAllBytes().decodeToString()
        assumeThat(decoded, equalTo("Привіт, світ!"))
    }
}