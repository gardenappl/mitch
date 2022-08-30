package garden.appl.mitch.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import garden.appl.mitch.Utils
import java.util.*


/**
 * Used to override locale, see https://stackoverflow.com/a/40704077/5701177
 */
class MitchContextWrapper private constructor(val systemLocale: Locale, base: Context)
    : ContextWrapper(base) {

    companion object {
        const val LOGGING_TAG = "MitchContextWrapper"

        @JvmStatic
        fun wrap(context: Context, language: String): MitchContextWrapper {
            val config = context.resources.configuration

            val systemLocale = Utils.getPreferredLocale(config)
//            Log.d(LOGGING_TAG, "Current locale: $systemLocale")
//            Log.d(LOGGING_TAG, "Wrapper locale: $language")

            if (language != "" && systemLocale.language != language) {
                val langComponents = language.split('_', '-')
                val locale = if (langComponents.size == 1)
                    Locale(langComponents[0])
                else
                    Locale(langComponents[0], langComponents[1])
                Locale.setDefault(locale)
                setLocale(config, locale)
            }

            val wrappedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createConfigurationContext(config)
            } else {
                @Suppress("deprecation")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                context
            }

            return MitchContextWrapper(systemLocale, wrappedContext)
        }

        private fun setLocale(config: Configuration, locale: Locale) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale)
            } else {
                @Suppress("deprecation")
                config.locale = locale
            }
        }
    }
}