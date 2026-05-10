package org.kasumi321.ushio.phitracker.data.platform

/**
 * Platform-agnostic app metadata for About screen.
 *
 * - [versionName]: SemVer or custom version string (e.g., "0.1.0")
 * - [buildTime]: Human-readable build/install timestamp (e.g., "2026-05-10 14:30")
 * - [buildType]: "Debug" or "Release"
 */
data class AppMetadata(
    val versionName: String,
    val buildTime: String,
    val buildType: String
)

expect fun getAppMetadata(): AppMetadata
