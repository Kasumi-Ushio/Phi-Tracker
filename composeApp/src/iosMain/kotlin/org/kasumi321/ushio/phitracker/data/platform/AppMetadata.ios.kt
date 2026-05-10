package org.kasumi321.ushio.phitracker.data.platform

/**
 * iOS metadata using static fallback values.
 *
 * CALLER ACTION REQUIRED: Update [versionName] and [buildTime] during
 * CI/archive builds (e.g., via Xcode build phase or Gradle task injection).
 * The [buildType] should remain "Release" for App Store / TestFlight builds.
 */
actual fun getAppMetadata(): AppMetadata = AppMetadata(
    versionName = "0.1.0",
    buildTime = "2026-05-10-dev",
    buildType = "Release"
)
