package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.buffer
import kotlin.time.Clock

class FileLogWriter(
    private val store: LogFileStore,
    private val maxFileSizeBytes: Long = 2L * 1024L * 1024L,
    private val maxRotatedFiles: Int = 5,
) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean = true

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        try {
            val fs = store.fileSystem
            val path = store.runtimeCurrentFile()
            if (!fs.exists(path)) {
                fs.write(path) { }
            }
            if ((fs.metadataOrNull(path)?.size ?: 0L) >= maxFileSizeBytes) {
                rotate(path)
            }
            val line = formatLine(severity, tag, message, throwable)
            val sink = fs.appendingSink(path).buffer()
            try {
                sink.writeUtf8(line)
                sink.writeUtf8("\n")
            } finally {
                sink.close()
            }
        } catch (e: Exception) {
            // Cannot delegate to AppLogger — this IS the file writer; recursion would occur.
            println("FileLogWriter: failed to write log entry: ${e.message}")
        }
    }

    private fun rotate(currentPath: okio.Path) {
        val fs = store.fileSystem
        val dir = currentPath.parent ?: return
        for (i in maxRotatedFiles - 1 downTo 1) {
            val src = dir / "runtime_$i.txt"
            val dst = dir / "runtime_${i + 1}.txt"
            if (fs.exists(src)) {
                fs.atomicMove(src, dst)
            }
        }
        fs.atomicMove(currentPath, dir / "runtime_1.txt")
        fs.write(currentPath) { }
        val excess = dir / "runtime_${maxRotatedFiles + 1}.txt"
        if (fs.exists(excess)) fs.delete(excess, mustExist = false)
    }

    private fun formatLine(
        severity: Severity,
        tag: String,
        message: String,
        throwable: Throwable?
    ): String {
        val instant = Clock.System.now()
        val epochMs = instant.toEpochMilliseconds()
        val kxInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
        val local = kxInstant.toLocalDateTime(TimeZone.currentSystemDefault())
        val millis = epochMs % 1000
        val time = "${local.date} ${local.hour.toString().padStart(2, '0')}:" +
                "${local.minute.toString().padStart(2, '0')}:" +
                "${local.second.toString().padStart(2, '0')}." +
                "${millis.toString().padStart(3, '0')}"
        val redactedMessage = LogRedactor.redact(message)
        val sb = StringBuilder()
        sb.append("$time ${severity.toChar()}/$tag: $redactedMessage")
        if (throwable != null) {
            sb.append("\n")
            sb.append(LogRedactor.redact(throwable.stackTraceToString()))
        }
        return sb.toString()
    }
}

private fun Severity.toChar(): Char = when (this) {
    Severity.Verbose -> 'V'
    Severity.Debug -> 'D'
    Severity.Info -> 'I'
    Severity.Warn -> 'W'
    Severity.Error -> 'E'
    Severity.Assert -> 'A'
}
