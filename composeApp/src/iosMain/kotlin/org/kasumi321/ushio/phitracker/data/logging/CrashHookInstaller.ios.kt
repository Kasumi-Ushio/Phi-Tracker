package org.kasumi321.ushio.phitracker.data.logging

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.buffer
import kotlin.time.Clock

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual object CrashHookInstaller {

    private var installed = false

    actual fun install(store: LogFileStore) {
        if (installed) return
        installed = true

        setUnhandledExceptionHook { throwable ->
            try {
                writeCrashLog(store, throwable)
            } catch (writeFailure: Exception) {
                println("CrashHookInstaller: failed to write crash log: ${writeFailure.message}")
            }
        }
    }

    private fun writeCrashLog(store: LogFileStore, throwable: Throwable) {
        val path = store.crashLogPath("kotlin_crash.txt")
        val fs = FileSystem.SYSTEM
        val parent = path.parent ?: return
        if (!fs.exists(parent)) fs.createDirectories(parent)

        val instant = Clock.System.now()
        val epochMs = instant.toEpochMilliseconds()
        val kxInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
        val local = kxInstant.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = "${local.date} ${local.hour.toString().padStart(2, '0')}:" +
                "${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"

        val sb = StringBuilder()
        sb.appendLine("=== Kotlin/Native Unhandled Exception ===")
        sb.appendLine("Time: $time")
        sb.appendLine("Exception: ${throwable::class.qualifiedName}: ${throwable.message}")
        sb.appendLine()
        sb.appendLine(throwable.stackTraceToString())

        val sink = fs.sink(path).buffer()
        try {
            sink.writeUtf8(LogRedactor.redact(sb.toString()))
        } finally {
            sink.close()
        }
    }
}
