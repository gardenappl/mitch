package garden.appl.mitch.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.acra.dialog.CrashReportDialogHelper
import garden.appl.mitch.R

class CrashDialog : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val acraHelper = CrashReportDialogHelper(this, intent)

        AlertDialog.Builder(this).run {
            setTitle(R.string.bug_report_dialog_title)
            setMessage(R.string.bug_report_dialog)
            setIcon(R.drawable.ic_baseline_warning_24)

            setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                acraHelper.sendCrash(null, null)
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            setOnDismissListener {
                acraHelper.cancelReports()
                this@CrashDialog.finish()
            }
            show()
        }
    }
}