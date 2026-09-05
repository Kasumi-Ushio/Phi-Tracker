@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import kotlin.native.Platform
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier

actual fun getAppMetadata(): AppMetadata {
    val bundle = NSBundle.mainBundle
    val versionName = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val buildType = bundle.objectForInfoDictionaryKey("PhiTrackerBuildType") as? String

    return AppMetadata(
        versionName = versionName?.takeIf { it.isNotBlank() } ?: "0.1.0",
        buildTime = formatBuildTime(IOS_BUILD_TIME_MILLIS),
        buildType = buildType?.takeIf { it.isNotBlank() } ?: if (Platform.isDebugBinary) "Debug" else "Release"
    )
}

private fun formatBuildTime(millis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
    return NSDateFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
        dateFormat = "yyyy-MM-dd HH:mm"
    }.stringFromDate(date)
}
