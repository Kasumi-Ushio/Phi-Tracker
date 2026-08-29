package org.kasumi321.ushio.phitracker.ui.settings

import kotlinx.coroutines.flow.update
import org.kasumi321.ushio.phitracker.data.logging.AppLogger

fun SettingsViewModel.exportRuntimeLogText(): String =
    runtimeLogExporter.buildExportText().also { AppLogger.event("log", "runtime_export") }

fun SettingsViewModel.exportCrashLogText(): String =
    crashReportExporter.buildExportText().also { AppLogger.event("log", "crash_export") }

fun SettingsViewModel.clearAllLogs(): Boolean {
    val runtime = runCatching { runtimeLogExporter.clearLogs() }
    AppLogger.event("log", "runtime_clear", mapOf("status" to if (runtime.isSuccess) "success" else "failed", "error" to (runtime.exceptionOrNull()?.message ?: "")))
    val crash = runCatching { crashReportExporter.clearReports() }
    AppLogger.event("log", "crash_clear", mapOf("status" to if (crash.isSuccess) "success" else "failed", "error" to (crash.exceptionOrNull()?.message ?: "")))
    mutableUiState.update { it.copy(hasRuntimeLogs = runtimeLogExporter.hasLogs(), hasCrashLogs = crashReportExporter.hasReports()) }
    return runtime.isSuccess && crash.isSuccess
}
