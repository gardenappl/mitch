package garden.appl.mitch.ui

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView


/**
 * A hacky workaround to expose the content width
 */
class MitchWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : WebView(context, attrs, defStyleAttr) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    val contentWidth: Float
        get() = (computeHorizontalScrollRange().toFloat() / resources.displayMetrics.density)

    fun redirectBlobUrlToDataUrl(blobUrl: String, fileName: String) {
        evaluateJavascript("""
            fetch("$blobUrl").then(r => r.blob()).then(blob => {
                // https://stackoverflow.com/a/30407959/5701177
                var reader = new FileReader();
                reader.onload = function(e) {
                    // https://stackoverflow.com/a/15832662/5701177
                    var link = document.createElement("a");
                    link.download = "$fileName";
                    link.href = e.target.result;
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                    delete link;
                }
                reader.readAsDataURL(blob);
            });
            """, null)
    }
}