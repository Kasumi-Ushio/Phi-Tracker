package org.kasumi321.ushio.phitracker.data.logging

expect object CrashHookInstaller {
    fun install(store: LogFileStore)
}
