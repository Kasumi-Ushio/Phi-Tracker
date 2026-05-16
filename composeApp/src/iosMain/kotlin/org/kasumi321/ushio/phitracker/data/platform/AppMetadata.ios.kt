@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import kotlin.native.Platform
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSLocale
import platform.Foundation.localeWithLocaleIdentifier

actual fun getAppMetadata(): AppMetadata {
    val bundle = NSBundle.mainBundle
    val versionName = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val buildType = bundle.objectForInfoDictionaryKey("PhiTrackerBuildType") as? String

    return AppMetadata(
        versionName = versionName?.takeIf { it.isNotBlank() } ?: "0.1.0",
        buildTime = bundleBuildTime(bundle),
        buildType = buildType?.takeIf { it.isNotBlank() } ?: if (Platform.isDebugBinary) "Debug" else "Release"
    )
}

private fun bundleBuildTime(bundle: NSBundle): String {
    val executablePath = bundle.executablePath ?: bundle.bundlePath
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(executablePath, error = null)
    val modificationDate = attributes?.get(NSFileModificationDate) as? NSDate ?: return "unknown"

    return NSDateFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
        dateFormat = "yyyy-MM-dd HH:mm"
    }.stringFromDate(modificationDate)
}
