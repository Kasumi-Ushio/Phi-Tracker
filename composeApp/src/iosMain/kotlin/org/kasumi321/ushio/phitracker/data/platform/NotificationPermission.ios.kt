package org.kasumi321.ushio.phitracker.data.platform

actual fun hasCrashNotificationPermission(): Boolean = true

actual fun requestCrashNotificationPermission(onResult: (Boolean) -> Unit) {
    onResult(true)
}
