package garden.appl.mitch.client

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.Mitch
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.client.ItchWebsiteParser.getPurchasedInfo
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.installation.Installation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Collections

object ItchWebsiteParser {
    class UploadNotFoundException(uploadId: Int) : RuntimeException(uploadId.toString())

    data class DownloadUrl(val url: String, val isPermanent: Boolean, val isStorePage: Boolean) {
        val downloadKey: String?
            get() {
                if (isFree)
                    return null
                return url.toUri().lastPathSegment
            }

        val isFree: Boolean
            get() {
                return isStorePage || !isPermanent
            }
    }

    data class PurchasedInfo(
        val ownershipReasonHtml: String,
        val downloadPage: String
    )

    data class PaymentInfo(
        val messageHtml: String,
        val isPaymentOptional: Boolean
    )

    private const val LOGGING_TAG = "ItchWebsiteParser"
    const val UNKNOWN_LOCALE = "Unknown"
    const val ENGLISH_LOCALE = "en"

    fun getGameInfoForStorePage(storePageDoc: Document, gamePageUrl: String): Game? {
        val gameId: Int = ItchWebsiteUtils.getGameId(storePageDoc) ?: return null
        Log.d(LOGGING_TAG, "Getting info for $gamePageUrl")
        val name: String = getGameName(storePageDoc)

        val thumbnailUrl = storePageDoc.head().selectFirst("[property=\"og:image\"]")?.attr("content")
        val faviconUrl = storePageDoc.head().selectFirst("link[rel=\"icon\"]")?.attr("href")

        val infoTable = getInfoTable(storePageDoc)
        val authorName = getAuthorName(gamePageUrl.toUri(), infoTable)
        val lastDownloadTimestamp: String? = getTimestamp(infoTable)

        val iframe = getIframe(storePageDoc)
        val iframeHtml = iframe?.outerHtml()
        val webEntryPoint = iframe?.attr("src")

        return Game(
            gameId = gameId,
            name = name,
            author = authorName,
            storeUrl = gamePageUrl,
            thumbnailUrl = thumbnailUrl,
            lastUpdatedTimestamp = lastDownloadTimestamp,
            locale = getLocale(storePageDoc),
            faviconUrl = faviconUrl,
            webIframe = iframeHtml,
            webEntryPoint = webEntryPoint
        )
    }

    fun getInstallations(doc: Document): List<Installation> {
        return parseInstallations(doc, null)
    }

    fun getPendingInstallation(doc: Document, uploadId: Int): Installation {
        val installList = parseInstallations(doc, uploadId)
        if (installList.isEmpty())
            throw UploadNotFoundException(uploadId)
        else
            return installList.first()
    }

    fun hasAndroidInstallation(doc: Document): Boolean {
        return doc.selectFirst(".icon-android") != null
    }

    fun hasWindowsMacLinuxInstallation(doc: Document): Boolean {
        return doc.selectFirst(".icon-windows8, .icon-apple, .icon-tux") != null
    }

    fun getInstallationsPlatforms(doc: Document): List<Int> {
        return doc.getElementsByClass("upload").map { upload -> getPlatformsForUpload(upload) }
    }

    private fun parseInstallations(doc: Document, requiredUploadId: Int?): List<Installation> {
        if (!ItchWebsiteUtils.hasGameDownloadLinks(doc))
            throw IllegalStateException("Unparse-able game page")

        val uploadDivs: List<Element>
        val uploadButtons = doc.getElementsByAttribute("data-upload_id")
        val uploadIds = uploadButtons.map { element ->
            Integer.parseInt(element.attr("data-upload_id"))
        }

        if (requiredUploadId != null) {
            val uploadButton = uploadButtons.find { element ->
                element.attr("data-upload_id") == requiredUploadId.toString()
            }

            if (uploadButton == null) {
                if (doc.selectFirst(".uploads") != null)
                    throw UploadNotFoundException(requiredUploadId)
                else
                    throw IllegalStateException("Unparse-able game page")
            }
            uploadDivs = Collections.singletonList(uploadButton.parent()!!)
        } else {
            uploadDivs = uploadButtons.first()!!.parent()!!.parent()!!.children()
        }

        val locale = getLocale(doc)
        val gameId = ItchWebsiteUtils.getGameId(doc)!!

        val result = ArrayList<Installation>()

        for (uploadDiv in uploadDivs) {
            val platforms = getPlatformsForUpload(uploadDiv)

            val uploadNameDiv = uploadDiv.selectFirst(".upload_name")!!
            val name = uploadNameDiv.selectFirst(".name")!!.attr("title")

            // TODO: External download link
            val fileSize = uploadNameDiv.selectFirst(".file_size")?.child(0)?.html()
                ?: continue

            val uploadId = requiredUploadId
                ?: Integer.parseInt(uploadDiv.selectFirst(".download_btn")!!.attr("data-upload_id"))

            //These may or may not exist
            var versionName: String? = null
            var versionDate: String? = null

            val buildRow = uploadNameDiv.nextElementSibling()
            if (buildRow != null) {
                if (buildRow.hasClass("upload_date"))
                    versionDate = buildRow.child(0).attr("title")

                val versionNameDiv = buildRow.selectFirst(".version_name")
                if (versionNameDiv != null)
                    versionName = versionNameDiv.html()

                val versionDateDiv = buildRow.selectFirst(".version_date")
                if (versionDateDiv != null)
                    versionDate = versionDateDiv.child(0).attr("title")
            }
            result.add(
                Installation(
                    gameId = gameId,
                    uploadId = uploadId,
                    availableUploadIds = uploadIds,
                    locale = locale,
                    version = versionName,
                    uploadTimestamp = versionDate,
                    uploadName = name,
                    fileSize = fileSize,
                    platforms = platforms,
                )
            )
        }
        return result
    }

    private fun getPlatformsForUpload(uploadDiv: Element): Int {
        val icons = uploadDiv.getElementsByClass("icon")
        var platforms = Installation.PLATFORM_NONE

        for (icon in icons) {
            if (icon.hasClass("icon-android"))
                platforms = platforms or Installation.PLATFORM_ANDROID
            else if (icon.hasClass("icon-windows8"))
                platforms = platforms or Installation.PLATFORM_WINDOWS
            else if (icon.hasClass("icon-apple"))
                platforms = platforms or Installation.PLATFORM_MAC
            else if (icon.hasClass("icon-tux"))
                platforms = platforms or Installation.PLATFORM_LINUX
        }
        return platforms
    }

    fun getStoreUrlFromDownloadPage(downloadUri: Uri): String {
        return "https://${downloadUri.host}/${downloadUri.pathSegments[0]}"
    }

    fun getAuthorUrlFromGamePage(gamePageUri: Uri): String {
        return "https://${gamePageUri.host}"
    }

    fun getPurchasedInfo(doc: Document): List<PurchasedInfo> {
        val purchaseBanner = doc.selectFirst(".purchase_banner_inner") ?: return emptyList()

        Log.d(LOGGING_TAG, "Purchased game")
        return purchaseBanner.getElementsByClass("key_row").sortedByDescending {
            val price = it.selectFirst(".purchase_price") ?: return@sortedByDescending 0
            return@sortedByDescending price.html().removePrefix("$").replace(".", "").toInt()
        }.map { downloadButtonRow ->
            //Reformat cloned element

            val ownershipReason = downloadButtonRow.selectFirst(".ownership_reason")!!.clone()

            val purchasedPrice: Element? = ownershipReason.selectFirst(".purchase_price")
            purchasedPrice?.replaceWith(Element("b").text(purchasedPrice.ownText()))

            val ownDate: Element? = ownershipReason.selectFirst(".own_date")
            ownDate?.replaceWith(Element("i").text(ownDate.ownText()))

            PurchasedInfo(
                ownershipReasonHtml = ownershipReason.html(),
                downloadPage = downloadButtonRow.selectFirst(".button")!!.attr("href")
            )
        }
    }

    /**
     * Should be called if [getPurchasedInfo] returns null
     * @return null if the game does not accept payments
     */
    fun getPaymentInfo(doc: Document): PaymentInfo? {
        val message = doc.selectFirst(".buy_message")?.clone() ?: return null
        Log.d(LOGGING_TAG, "Original: $message")

        //TODO: show URL for sale link?
        message.selectFirst(".sale_link")?.remove()

        message.selectFirst(".original_price")?.let { originalPrice ->
            val color = Utils.asHexCode(ColorUtils.blendARGB(
                ItchWebsiteUtils.getBackgroundUIColor(doc)!!,
                Color.WHITE,
                0.5f
            ))
            originalPrice.replaceWith(Element("strike")
                .text(originalPrice.text())
                .attr("style", "color: $color")
            )
        }

        message.selectFirst(".sub")?.let { sub ->
            val color = Utils.asHexCode(ColorUtils.blendARGB(
                ItchWebsiteUtils.getBackgroundUIColor(doc)!!,
                Color.WHITE,
                0.5f
            ))
            sub.replaceWith(Element("span")
                .text(sub.ownText())
                .attr("style", "color: $color")
            )
        }

        val isPaymentOptional = message.selectFirst("[itemprop=price]")?.let { price ->
            val color = Utils.asHexCode(ItchWebsiteUtils.getAccentFgUIColor(doc)!!)
            price.replaceWith(Element("b")
                .text(' ' + price.ownText() + ' ')
                .attr("style", "color: $color")
            )
            return@let false
        } ?: true
        val html = message.html().lines().joinToString(separator = " ")
        Log.d(LOGGING_TAG, "Modified: $html")
        return PaymentInfo(html, isPaymentOptional)
    }

    /**
     * @return null if user does not have access
     */
    suspend fun getDownloadUrl(doc: Document, storeUrl: String): DownloadUrl? {
        //The game has been bought
        val purchaseInfo = getPurchasedInfo(doc)
        if (purchaseInfo.isNotEmpty())
            return DownloadUrl(purchaseInfo.first().downloadPage, isPermanent = true, isStorePage = false)

        //The game is free and the store page provides download links
        if (doc.selectFirst(".download_btn") != null)
            return DownloadUrl(storeUrl, isPermanent = true, isStorePage = true)

        //The game is free but accepts donations and hasn't been paid for
        return fetchDownloadUrlFromStorePage(storeUrl)
    }

    /**
     * Requests the URL of a web page with available downloads for an itch.io game.
     * This download URL might be temporary, or might not be available at all.
     * @param storeUrl URL of a game's store page.
     * @return For paid games, always returns null, otherwise returns a URL of a downloads page.
     */
    suspend fun fetchDownloadUrlFromStorePage(storeUrl: String): DownloadUrl? {
        val result = withContext(Dispatchers.IO) {
            Log.d(LOGGING_TAG, "Fetching download URL for $storeUrl")
            val storeUriParsed = Uri.parse(storeUrl)

            val uriBuilder = storeUriParsed.buildUpon()
            uriBuilder.appendPath("download_url")
            val getDownloadPathUri = uriBuilder.build()
            val cookie = CookieManager.getInstance()?.getCookie(storeUrl)

            val form = FormBody.Builder().run {
                // TODO: wat
                cookie?.let {
                    add("csrf_token", it)
                }
                build()
            }
            val request = Request.Builder().run {
                url(getDownloadPathUri.toString())
                cookie?.let {
                    addHeader("Cookie", it)
                }
                post(form)

                build()
            }
            Mitch.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw IOException("Unexpected code $response")

                //Log.d(LOGGING_TAG, "Response: ${response.body!!.string()}")
                response.body.string()
            }
        }

        val resultJson = JSONObject(result)
        Log.d(LOGGING_TAG, "Result for $storeUrl: $resultJson")
        if (resultJson.has("errors")) {
            val errorsArray = resultJson.getJSONArray("errors")
            for (i in 0 until errorsArray.length()) {
                if (errorsArray.getString(i) == "you must buy this game to download")
                    return null
            }
        }

        if (resultJson.has("url"))
            return DownloadUrl(
                    resultJson.getString("url"),
                    isPermanent = false,
                    isStorePage = false
            )
        else
            throw RuntimeException("Unexpected JSON response")
    }

    fun getLocale(doc: Document): String {
        val scripts = doc.head().getElementsByTag("script")
        for (script in scripts) {
            val html = script.html().trimStart()
            if (html.startsWith("window.itchio_locale"))
                return html.substring(24, 26)
        }

        if (doc.body().hasClass("locale_en"))
            return ENGLISH_LOCALE
        return UNKNOWN_LOCALE
    }

    private fun getTimestamp(infoTable: Element): String? {
        var timestamp: String? = infoTable.child(0).child(1).child(0).attr("title")
        if (timestamp?.contains('@') != true)
            timestamp = null

        return timestamp
    }

    private fun getAuthorName(gamePageUri: Uri, infoTable: Element): String {
        val authorUrl = getAuthorUrlFromGamePage(gamePageUri)
        // Hardcoded for tools published by itch.io themselves,
        // such as https://itchio.itch.io/butler
        if (authorUrl == "https://itchio.itch.io")
            return "itch.io"
        Log.d(LOGGING_TAG, "Author URL: $authorUrl")
        return infoTable.selectFirst("[href=\"$authorUrl\"]")!!.html()
    }

    /*fun getAuthorName(doc: Document, gamePageUri: Uri): String {
        return getAuthorName(gamePageUri, getInfoTable(doc))
    }*/

    private fun getInfoTable(doc: Document): Element {
        return doc.body().selectFirst(".game_info_panel_widget")!!.child(0).child(0)
    }

    fun getGameName(doc: Document): String {
        if (ItchWebsiteUtils.isPurchasePage(doc)) {
            return doc.selectFirst("h1")!!.child(0).text()
        }

        if (ItchWebsiteUtils.isDownloadPage(doc)) {
            return doc.selectFirst("h2")!!.child(0).text()
        }

        if (ItchWebsiteUtils.isStorePage(doc)) {
            val jsonObjects =
                doc.head().getElementsByAttributeValue("type", "application/ld+json")
            val productJsonString: String = jsonObjects[1].html()
            val jsonObject = JSONObject(productJsonString)
            return jsonObject.getString("name")
        }

        if (ItchWebsiteUtils.isDevlogPage(doc)) {
            return (doc.selectFirst(".game_title")
                ?: doc.selectFirst(".game_metadata")!!.selectFirst("h3"))!!
                .html()
        }

        throw IllegalArgumentException("Document is not related to game")
    }

    fun getUserName(doc: Document): String {
        if (ItchWebsiteUtils.isUserPage(doc)) {
            return doc.getElementById("profile_header")!!.selectFirst("h1")!!.text()
        }

        throw IllegalArgumentException("Document is not a user page")
    }

    fun getForumOrJamName(doc: Document): String {
        return doc.selectFirst(".jam_title_header, .game_summary h1")?.text()
            ?: throw IllegalArgumentException("Could not find game jam or forum name")
    }

    private fun getIframe(doc: Document, placeholder: Element? = null): Element? {
        val placeholder = placeholder ?: doc.selectFirst(".iframe_placeholder")
        if (placeholder != null)
            return Jsoup.parse(placeholder.attr("data-iframe")).selectFirst("iframe")
        else
            return doc.getElementById("game_drop")
    }

    fun getWebGameLabel(context: Context, doc: Document): String? {
        val placeholder = doc.selectFirst(".iframe_placeholder")
        return placeholder?.selectFirst(".load_iframe_btn")?.text()
            ?: context.getString(R.string.game_web_play_default_type)
    }
}