package org.kasumi321.ushio.phitracker.data.platform

import android.content.pm.ApplicationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.kasumi321.ushio.phitracker.BuildConfig

actual fun getAppMetadata(): AppMetadata {
    val context = AndroidPlatformContext.applicationContext
    if (context == null) {
        return AppMetadata(
            versionName = "0.1.0",
            buildTime = formatBuildTime(),
            buildType = "Release"
        )
    }
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppMetadata(
            versionName = packageInfo.versionName ?: "0.1.0",
            buildTime = formatBuildTime(),
            buildType = if (isDebuggable) "Debug" else "Release"
        )
    } catch (e: Exception) {
        AppMetadata(
            versionName = "0.1.0",
            buildTime = formatBuildTime(),
            buildType = "Release"
        )
    }
}

private fun formatBuildTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(BuildConfig.BUILD_TIME_MILLIS))
