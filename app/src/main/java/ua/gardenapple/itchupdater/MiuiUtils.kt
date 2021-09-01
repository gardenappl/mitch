package ua.gardenapple.itchupdater

import android.annotation.SuppressLint

import android.os.Build

import java.lang.Exception

// Converted from Java from https://github.com/Aefyr/SAI/blob/master/app/src/main/java/com/aefyr/sai/utils/MiuiUtils.java
object MiuiUtils {
    private fun isMiui(): Boolean {
        return !Utils.tryGetSystemProperty("ro.miui.ui.version.name").isNullOrEmpty()
    }

    private fun getActualMiuiVersion(): String {
        return Build.VERSION.INCREMENTAL
    }

    @SuppressLint("PrivateApi")
    private fun isMiuiOptimizationDisabled(): Boolean {
        if (Utils.tryGetSystemProperty("persist.sys.miui_optimization") == "0") {
            return true
        } else try {
            return Class.forName("android.miui.AppOpsUtils")
                .getDeclaredMethod("isXOptMode")
                .invoke(null) as Boolean
        } catch (e: Exception) {
            return false
        }
    }

    fun doesSessionInstallerWork(): Boolean {
        return !isMiui()
                || Utils.isVersionNewer(getActualMiuiVersion(), "20.2.20") == true
                || isMiuiOptimizationDisabled()
    }
}