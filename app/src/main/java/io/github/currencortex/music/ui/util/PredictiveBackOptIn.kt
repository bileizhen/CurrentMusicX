package io.github.currencortex.music.ui.util

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object PredictiveBackOptIn {
    fun apply(applicationInfo: ApplicationInfo, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            ApplicationInfo::class.java
                .getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(applicationInfo, enabled)
        }.onFailure { Log.w("PredictiveBack", "Cannot update the Android callback policy", it) }
    }
}
