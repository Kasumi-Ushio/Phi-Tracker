package org.kasumi321.ushio.phitracker.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeLogExporter {

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun hasLogs(context: Context): Boolean = RuntimeLogCollector.listLogFiles(context).isNotEmpty()

    fun buildExportText(context: Context): String {
        val files = RuntimeLogCollector.listLogFiles(context)
        if (files.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("Phi Tracker Runtime Logs")
        sb.appendLine("Generated at: ${timestampFormat.format(Date())}")
        sb.appendLine("Count: ${files.size}")
        sb.appendLine()

        files.forEachIndexed { index, file ->
            sb.appendLine("===== Log ${index + 1} / ${files.size} =====")
            sb.appendLine("File: ${file.name}")
            sb.appendLine("Modified: ${timestampFormat.format(Date(file.lastModified()))}")
            sb.appendLine()
            sb.appendLine(file.safeReadText())
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun File.safeReadText(): String {
        return runCatching { readText(Charsets.UTF_8) }
            .getOrElse { "Failed to read runtime log file: ${it.message ?: "unknown error"}" }
    }
}
