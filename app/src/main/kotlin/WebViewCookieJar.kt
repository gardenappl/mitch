package garden.appl.mitch

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return CookieManager.getInstance().getCookie(url.toString())
            ?.split("; ")
            ?.map { cookieString -> Cookie.parse(url, cookieString)!! }
            ?: emptyList()
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>
    ) {
        val manager = CookieManager.getInstance()
        cookies.forEach { cookie ->
            manager.setCookie(url.toString(), cookie.toString())
        }
    }
}