package garden.appl.mitch.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.LayoutInflater
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import garden.appl.mitch.R
import garden.appl.mitch.databinding.DialogWebPromptBinding

abstract class MitchWebChromeClient(
    private val openDocumentLauncher: ActivityResultLauncher<Array<String>>,
    private val openMultipleDocumentsLauncher: ActivityResultLauncher<Array<String>>
) : WebChromeClient() {
    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult
    ): Boolean {
        val context = view.context
        val dialog = AlertDialog.Builder(context).apply {
            setTitle(context.getString(R.string.dialog_web_alert, url))
            setMessage(message)
            setPositiveButton(android.R.string.ok) { _, _ ->
                result.confirm()
            }
            setCancelable(false)

            create()
        }
        dialog.show()
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult
    ): Boolean {
        val context = view.context
        val dialog = AlertDialog.Builder(context).apply {
            setTitle(context.getString(R.string.dialog_web_prompt, url))
            setMessage(message)
            setPositiveButton(android.R.string.ok) { _, _ ->
                result.confirm()
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                result.cancel()
            }
            setOnCancelListener {
                result.cancel()
            }

            create()
        }
        dialog.show()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String?,
        result: JsPromptResult
    ): Boolean {
        val context = view.context
        val binding = DialogWebPromptBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(context).apply {
            setTitle(context.getString(R.string.dialog_web_prompt, url))
            binding.message.text = message
            binding.input.setText(defaultValue)
            setView(binding.root)

            setPositiveButton(android.R.string.ok) { _, _ ->
                result.confirm(binding.message.text.toString())
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                result.cancel()
            }
            setOnCancelListener {
                result.cancel()
            }

            create()
        }
        dialog.show()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        val launcher = when (fileChooserParams.mode) {
            FileChooserParams.MODE_OPEN -> openDocumentLauncher
            FileChooserParams.MODE_OPEN_MULTIPLE -> openMultipleDocumentsLauncher
            else -> return false
        }
        setFileChooserCallback(filePathCallback)

        try {
            launcher.launch(fileChooserParams.acceptTypes)
        } catch (e: ActivityNotFoundException) {
            val context = webView.context
            Toast.makeText(context, context.getString(R.string.popup_no_file_manager), Toast.LENGTH_LONG)
                .show()
            return false
        }
        return true
    }

    abstract fun setFileChooserCallback(callback: ValueCallback<Array<Uri>>)
}