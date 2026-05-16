package org.kasumi321.ushio.phitracker.data.logging

class RuntimeLogExporter(private val store: LogFileStore) {
    fun hasLogs(): Boolean = store.hasRuntimeLogs()
    fun buildExportText(): String = store.buildRuntimeExport()
    fun clearLogs() = store.clearRuntimeLogs()
}
