package ua.gardenapple.itchupdater.ui

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView


/**
 * A hacky workaround to expose the content width
 */
class MitchWebView : WebView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleAttrRes: Int) : super(context, attrs, defStyleAttr, defStyleAttrRes)

    val contentWidth: Float
        get() = (computeHorizontalScrollRange().toFloat() / resources.displayMetrics.density)
}