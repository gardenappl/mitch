package garden.appl.mitch.ui

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.webkit.WebView


/**
 * A hacky workaround to expose the content width
 */
class MitchWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : WebView(context, attrs, defStyleAttr) {

    val contentWidth: Float
        get() = (computeHorizontalScrollRange().toFloat() / resources.displayMetrics.density)

    override fun onSaveInstanceState(): Parcelable? {
        return super.onSaveInstanceState()
    }
}