package ua.gardenapple.itchupdater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.FileProvider
import com.github.ajalt.colormath.ConvertibleColor
import com.github.ajalt.colormath.fromCss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.math.min

class Utils {
    class ErrorReport(message: String) : Throwable(message)

    companion object {
        private const val LOG_LIMIT: Int = 1000

        /**
         * Logcat normally has a limit of 1000 characters.
         * This function splits long strings into multiple log entries.
         */
        fun logPrintLong(priority: Int, tag: String, string: String) {
            for (i in string.indices step LOG_LIMIT) {
                Log.println(priority, tag, string.substring(i, min(string.length, i + LOG_LIMIT)))
            }
        }

        /**
         * logPrintLong with Debug priority
         */
        fun logLongD(tag: String, string: String) {
            logPrintLong(Log.DEBUG, tag, string)
        }

        fun getCurrentUnixTime(): Long {
            return System.currentTimeMillis() / 1000
        }

        suspend fun copy(input: InputStream, output: OutputStream) = withContext(Dispatchers.IO) {
            val BUFFER_SIZE = 1024 * 1024

            val buffer = ByteArray(BUFFER_SIZE)
            var n: Int
            while (true) {
                n = input.read(buffer)
                if (n == -1)
                    break
                output.write(buffer, 0, n)
            }
        }

//        fun Int.hasFlag(flag: Int): Boolean {
//            return this and flag == flag
//        }

        //https://stackoverflow.com/a/10600736/5701177
        fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null)
                return drawable.bitmap

            val bitmap: Bitmap
            if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                // Single color bitmap will be created of 1x1 pixel
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            } else {
                bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            }

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }

        fun toString(bundle: Bundle?): String {
            if (bundle == null)
                return "null"

            val sb = StringBuilder()
            sb.append("[ ")
            for (key in bundle.keySet()) {
                sb.append("$key = ${bundle.get(key)}, ")
            }
            sb.append(" ]")
            return sb.toString()
        }
        
        fun toString(e: Throwable): String {
            val errorWriter = StringWriter()
            errorWriter.appendLine(e.localizedMessage)
            e.printStackTrace(PrintWriter(errorWriter))

            e.cause?.let { cause ->
                errorWriter.append("Cause: ")
                errorWriter.append(toString(cause))
            }

            return errorWriter.toString()
        }

        /**
         * Wrapper method for external library
         * TODO: minimal CSS color parsing without library?
         */
        fun parseCssColor(color: String): Int {
            return ConvertibleColor.fromCss(color).toRGB().toPackedInt()
        }

        
        fun colorStateListOf(vararg mapping: Pair<IntArray, Int>): ColorStateList {
            val (states, colors) = mapping.unzip()
            return ColorStateList(states.toTypedArray(), colors.toIntArray())
        }

        fun colorStateListOf(@ColorInt color: Int): ColorStateList {
            return ColorStateList.valueOf(color)
        }

        /**
         * Similar to ContextCompat.getColor, except also aware of light/dark themes
         */
        @ColorInt
        @Suppress("DEPRECATION")
        fun getColor(context: Context, @ColorRes id: Int): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getColor(id)
            } else {
                val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                    when (id) {
                        R.color.colorBackground -> context.resources.getColor(R.color.colorPrimaryDark)
                        R.color.colorForeground -> context.resources.getColor(R.color.colorPrimary)
                    }
                } else {
                    when (id) {
                        R.color.colorBackground -> context.resources.getColor(R.color.colorPrimary)
                        R.color.colorForeground -> context.resources.getColor(R.color.colorPrimaryDark)
                    }
                }
                context.resources.getColor(id)
            }
        }
        
        fun getIntentForFile(context: Context, file: File, fileProvider: String): Intent {
            return Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    FileProvider.getUriForFile(context, fileProvider, file)
                else
                    Uri.fromFile(file)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        
        fun getInt(bundle: Bundle, key: String): Int? {
            return if (bundle.containsKey(key))
                bundle.getInt(key)
            else
                null
        }

        fun getLong(bundle: Bundle, key: String): Long? {
            return if (bundle.containsKey(key))
                bundle.getLong(key)
            else
                null
        }

        fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
            try {
                return packageManager.getApplicationInfo(packageName, 0).enabled
            } catch (e: PackageManager.NameNotFoundException) {
                return false
            }
        }

        fun fitsInInt(l: Long): Boolean {
            return l.toInt().toLong() == l
        }
    }
}
