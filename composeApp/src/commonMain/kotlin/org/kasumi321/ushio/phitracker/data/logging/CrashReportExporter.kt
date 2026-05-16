package org.kasumi321.ushio.phitracker.data.logging

class CrashReportExporter(private val store: LogFileStore) {
    fun hasReports(): Boolean = store.hasCrashLogs()
    fun buildExportText(): String = store.buildCrashExport()
    fun clearReports() = store.clearCrashLogs()
}
