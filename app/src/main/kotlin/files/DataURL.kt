package garden.appl.mitch.files

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class DataURL(val url: String) {
    private val startPos: Int
    private val base64: Boolean
    private val charset: String

    init {
        if (!isValid(url))
            throw IllegalArgumentException(url)
        startPos = url.indexOf(',') + 1
        val mediaType = url.substring("data:".length, startPos - 1)
        base64 = mediaType.endsWith(";base64")
        charset = Regex(""";charset=(.*?)(?:;.*)?$""").find(mediaType)?.groupValues?.getOrNull(1)
                ?: "US-ASCII"
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun toInputStream(): InputStream {
        val decoded = URLDecoder.decode(url.substring(startPos), charset)
        val byteArray = if (base64)
            Base64.decode(decoded)
        else {
            decoded.toByteArray(Charset.forName(charset))
        }
        return ByteArrayInputStream(byteArray)
    }

    companion object {
        fun isValid(url: String) = url.startsWith("data:")
    }
}