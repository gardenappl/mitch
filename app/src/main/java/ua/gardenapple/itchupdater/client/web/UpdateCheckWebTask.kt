package ua.gardenapple.itchupdater.client.web

import android.util.Log
import android.webkit.CookieManager
import org.jsoup.Jsoup
import ua.gardenapple.itchupdater.GamePlatform
import ua.gardenapple.itchupdater.GameStoreInfo
import ua.gardenapple.itchupdater.GameVersion
import ua.gardenapple.itchupdater.GameVersionInfo
import ua.gardenapple.itchupdater.client.UpdateCheckTask

class UpdateCheckWebTask : UpdateCheckTask() {

    override fun doInBackground(vararg params: GameStoreInfo): GameVersionInfo {
        val gameInfo = params[0]
        when (gameInfo.downloadUrl) {
            null -> return checkStorePage(gameInfo.storeUrl)
            else -> return checkDownloadPage(gameInfo.downloadUrl)
        }
    }

//    override fun onPostExecute(result: GameVersionInfo) {
//        Log.d(LOGGING_TAG, result.versions[GamePlatform.Android]!![0].versionName)
//        Log.d(LOGGING_TAG, result.versions[GamePlatform.Android]!![0].timestamp)
//    }

    private fun checkDownloadPage(url: String): GameVersionInfo {
        val MAX_DOCUMENT_SIZE = 30000 //size in bytes

        Log.d("Test", CookieManager.getInstance().getCookie("https://itch.io"))
        val doc = Jsoup.connect(url).header("Cookie", CookieManager.getInstance().getCookie("https://itch.io")).maxBodySize(MAX_DOCUMENT_SIZE).get()

        val maxLogSize = 1000
        val result = doc.html()
        Log.v("ItchUpd.WebExtractor", "Printing ${result.length}-long string...")
        for (i in 0..result.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > result.length) result.length else end
            Log.v("ItchUpd.WebExtractor", result.substring(start, end))
        }


        val versionsMap = HashMap<GamePlatform, ArrayList<GameVersion>>()


//        val icons = doc.getElementsByClass("icon-android")
        val icons = doc.getElementsByClass("icon-windows8")
        if (icons.isNotEmpty()) {
            versionsMap[GamePlatform.Android] = ArrayList()
            for(icon in icons) {
                val apkName = icon.parent().parent().getElementsByClass("name").attr("title")
                val fileSize = icon.parent().parent().getElementsByClass("file_size")[0].child(0).text()
                val timestamp = icon.parent().parent().parent().parent().nextElementSibling()
                        .getElementsByClass("version_date")[0].child(0).attr("title")
                val uploadID = icon.parent().parent().parent().previousElementSibling().attr("data-upload_id").toInt()

                versionsMap[GamePlatform.Android]!!.add(GameVersion(apkName, timestamp, true, fileSize, uploadID))
            }
        }
        return GameVersionInfo(versionsMap)
    }


    private fun checkStorePage(url: String): GameVersionInfo {
        val MAX_DOCUMENT_SIZE = 50000 //size in bytes

        val doc = Jsoup.connect(url).maxBodySize(MAX_DOCUMENT_SIZE).get()

        val maxLogSize = 1000
        val result = doc.html()
        Log.v("ItchUpd.WebExtractor", "Printing ${result.length}-long string...")
        for (i in 0..result.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > result.length) result.length else end
            Log.v("ItchUpd.WebExtractor", result.substring(start, end))
        }


        val versionsMap = HashMap<GamePlatform, ArrayList<GameVersion>>()

        val timestamp = doc.getElementsByClass("update_timestamp")[0]
            .getElementsByAttribute("title")[0].attr("title")

        val icons = doc.getElementsByClass("icon-android")
        if (icons.isNotEmpty()) {
            versionsMap[GamePlatform.Android] = ArrayList()
            for (icon in icons) {
                val apkName = icon.parent().parent().getElementsByClass("name").attr("title")
                val fileSize = icon.parent().parent().getElementsByClass("file_size")[0].child(0).text()
                versionsMap[GamePlatform.Android]!!.add(GameVersion(apkName, timestamp, false, fileSize))
            }
        }

        if (doc.getElementsByClass("load_iframe_btn").isNotEmpty()) {
            versionsMap[GamePlatform.Web] = arrayListOf(GameVersion.Web)
        }

        return GameVersionInfo(versionsMap)
    }
}