package ua.gardenapple.itchupdater.client

import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ua.gardenapple.itchupdater.ItchWebsiteUtils
import ua.gardenapple.itchupdater.Mitch
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

object ItchWebsiteParser {
    class UploadNotFoundException(uploadId: Int) : RuntimeException(uploadId.toString())

    data class DownloadUrl(val url: String, val isPermanent: Boolean, val isStorePage: Boolean) {
        val downloadKey: String?
            get() {
                if (isFree)
                    return null
                return Uri.parse(url).lastPathSegment
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

        val thumbnail = storePageDoc.head().selectFirst("[property=\"og:image\"]")
        val thumbnailUrl = if (thumbnail != null) {
            thumbnail.attr("content")
        } else {
            Log.d(LOGGING_TAG, "No thumbnail!")
            null
        }

        val infoTable = getInfoTable(storePageDoc)

        val authorName = getAuthorName(Uri.parse(gamePageUrl), infoTable)
        val lastDownloadTimestamp: String? = getTimestamp(infoTable)

        return Game(
            gameId = gameId,
            name = name,
            author = authorName,
            storeUrl = gamePageUrl,
            thumbnailUrl = thumbnailUrl,
            lastUpdatedTimestamp = lastDownloadTimestamp,
            locale = getLocale(storePageDoc)
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
            uploadDivs = Collections.singletonList(uploadButton.parent())
        } else {
            uploadDivs = uploadButtons.first().parent().parent().children()
        }

        val locale = getLocale(doc)
        val gameId = ItchWebsiteUtils.getGameId(doc)!!

        val result = ArrayList<Installation>()

        for (uploadDiv in uploadDivs) {
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

            val uploadNameDiv = uploadDiv.selectFirst(".upload_name")
            val name = uploadNameDiv.selectFirst(".name").attr("title")
            val fileSize = uploadNameDiv.selectFirst(".file_size").child(0).html()
            val uploadId = requiredUploadId
                ?: Integer.parseInt(uploadDiv.selectFirst(".download_btn").attr("data-upload_id"))

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

    fun getStoreUrlFromDownloadPage(downloadUri: Uri): String {
        return "https://${downloadUri.host}/${downloadUri.pathSegments[0]}"
    }

    fun getAuthorUrlFromGamePage(gamePageUri: Uri): String {
        return "https://${gamePageUri.host}"
    }

    /**
     * @return null if the user did not purchase this item
     */
    fun getPurchasedInfo(doc: Document): PurchasedInfo? {
        val purchaseBanner = doc.selectFirst(".purchase_banner_inner") ?: return null

        Log.d(LOGGING_TAG, "Purchased game")
        val downloadButtonRow = purchaseBanner.getElementsByClass("key_row").sortedBy {
            val price = it.selectFirst(".purchase_price") ?: return@sortedBy 0

            Log.d(LOGGING_TAG, "Paid: ${price.html().removePrefix("$").replace(".", "")}")
            return@sortedBy price.html().removePrefix("$").replace(".", "").toInt()
        }.last()


        //Reformat cloned element

        val ownershipReason = downloadButtonRow.selectFirst(".ownership_reason").clone()

        val purchasedPrice = ownershipReason.selectFirst(".purchase_price")
        purchasedPrice.replaceWith(Element("b").text(purchasedPrice.ownText()))

        val ownDate = ownershipReason.selectFirst(".own_date")
        ownDate.replaceWith(Element("i").text(ownDate.ownText()))


        return PurchasedInfo(
            ownershipReasonHtml = ownershipReason.html(),
            downloadPage = downloadButtonRow.selectFirst(".button").attr("href")
        )
    }

    /**
     * Should be called if [getPurchasedInfo] returns null
     * @return null if the game does not accept payments
     */
    fun getPaymentInfo(doc: Document): PaymentInfo? {
        val message = doc.selectFirst(".buy_message")?.clone() ?: return null
        val price = message.selectFirst("[itemprop=price]")
        val sub = message.selectFirst(".sub")

        if (sub != null) {
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

        if (price != null) {
            val color = Utils.asHexCode(ItchWebsiteUtils.getAccentFgUIColor(doc)!!)
            price.replaceWith(Element("b")
                .text(price.ownText())
                .attr("style", "color: $color")
            )
            return PaymentInfo(messageHtml = message.html(), isPaymentOptional = false)
        }
        return PaymentInfo(messageHtml = message.html(), isPaymentOptional = true)
    }

    /**
     * @return null if user does not have access
     */
    suspend fun getDownloadUrl(doc: Document, storeUrl: String): DownloadUrl? =
        withContext(Dispatchers.IO) {
            //The game has been bought
            val purchaseInfo = getPurchasedInfo(doc)
            if (purchaseInfo != null)
                return@withContext DownloadUrl(purchaseInfo.downloadPage, isPermanent = true, isStorePage = false)

            //The game is free and the store page provides download links
            if (doc.selectFirst("download_btn") != null)
                return@withContext DownloadUrl(storeUrl, isPermanent = true, isStorePage = true)

            //The game is free but accepts donations and hasn't been paid for
            return@withContext fetchDownloadUrlFromStorePage(storeUrl)
        }

    /**
     * Requests the URL of a web page with available downloads for an itch.io game.
     * This download URL might be temporary, or might not be available at all.
     * @param storeUrl URL of a game's store page.
     * @return For paid games, always returns null, otherwise returns a URL of a downloads page.
     */
    suspend fun fetchDownloadUrlFromStorePage(storeUrl: String): DownloadUrl? =
        withContext(Dispatchers.IO) {
            Log.d(LOGGING_TAG, "Fetching download URL for $storeUrl")
            val storeUriParsed = Uri.parse(storeUrl)

            val uriBuilder = storeUriParsed.buildUpon()
            uriBuilder.appendPath("download_url")
            val getDownloadPathUri = uriBuilder.build()
            val cookie = CookieManager.getInstance()?.getCookie(storeUrl)

            val form = FormBody.Builder().run {
                //I don't know if I can implement my own CSRF tokens, so just do whatever
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
            val result: String =
                Mitch.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful)
                        throw IOException("Unexpected code $response")

                    //Log.d(LOGGING_TAG, "Response: ${response.body!!.string()}")
                    response.body!!.string()
                }
            val resultJson = JSONObject(result)
            Log.d(LOGGING_TAG, "Result for $storeUrl: $resultJson")
            if (resultJson.has("errors")) {
                val errorsArray = resultJson.getJSONArray("errors")
                for (i in 0 until errorsArray.length()) {
                    if (errorsArray.getString(i) == "you must buy this game to download")
                        return@withContext null
                }

            }

            if (resultJson.has("url"))
                return@withContext DownloadUrl(
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
        var timestamp = infoTable.child(0).child(1).child(0).attr("title")
        if (timestamp?.contains('@') != true)
            timestamp = null

        return timestamp
    }

    private fun getAuthorName(gamePageUri: Uri, infoTable: Element): String {
        Log.d(LOGGING_TAG, "Author URL: ${getAuthorUrlFromGamePage(gamePageUri)}")
        return infoTable.selectFirst("[href=\"${getAuthorUrlFromGamePage(gamePageUri)}\"]").html()
    }

    /*fun getAuthorName(doc: Document, gamePageUri: Uri): String {
        return getAuthorName(gamePageUri, getInfoTable(doc))
    }*/

    private fun getInfoTable(doc: Document): Element {
        return doc.body().selectFirst(".game_info_panel_widget").child(0).child(0)
    }

    fun getGameName(doc: Document): String {
        if (ItchWebsiteUtils.isPurchasePage(doc)) {
            return doc.selectFirst("h1").child(0).text()
        }

        if (ItchWebsiteUtils.isDownloadPage(doc)) {
            return doc.selectFirst("h2").child(0).text()
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
                ?: doc.selectFirst(".game_metadata").selectFirst("h3"))
                .html()
        }

        throw IllegalArgumentException("Document is not related to game")
    }

    fun getUserName(doc: Document): String {
        if (ItchWebsiteUtils.isUserPage(doc)) {
            return doc.getElementById("profile_header").selectFirst("h1").text()
        }

        throw IllegalArgumentException("Document is not a user page")
    }

    fun getForumOrJamName(doc: Document): String {
        return doc.selectFirst(".jam_title_header, .game_summary h1")?.text()
            ?: throw IllegalArgumentException("Could not find game jam or forum name")
    }
}