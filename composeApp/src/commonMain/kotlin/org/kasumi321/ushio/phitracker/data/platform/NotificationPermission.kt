package org.kasumi321.ushio.phitracker.data.platform

expect fun hasCrashNotificationPermission(): Boolean

expect fun requestCrashNotificationPermission(onResult: (Boolean) -> Unit)
