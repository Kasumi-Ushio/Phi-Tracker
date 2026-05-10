package org.kasumi321.ushio.phitracker.data.platform

import android.content.pm.ApplicationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun getAppMetadata(): AppMetadata {
    val context = AndroidPlatformContext.applicationContext
    if (context == null) {
        return AppMetadata(
            versionName = "0.1.0",
            buildTime = "unknown",
            buildType = "Release"
        )
    }
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        AppMetadata(
            versionName = packageInfo.versionName ?: "0.1.0",
            buildTime = sdf.format(Date(packageInfo.lastUpdateTime)),
            buildType = if (isDebuggable) "Debug" else "Release"
        )
    } catch (e: Exception) {
        AppMetadata(
            versionName = "0.1.0",
            buildTime = "unknown",
            buildType = "Release"
        )
    }
}
