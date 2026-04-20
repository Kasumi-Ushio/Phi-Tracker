package org.kasumi321.ushio.phitracker.utils

import android.content.Context
import org.acra.file.ReportLocator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReportExporter {

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun buildExportText(context: Context): String {
        val reportFiles = listReportFiles(context)
        if (reportFiles.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("Phi Tracker Crash Reports")
        sb.appendLine("Generated at: ${timestampFormat.format(Date())}")
        sb.appendLine("Count: ${reportFiles.size}")
        sb.appendLine()

        reportFiles.forEachIndexed { index, file ->
            sb.appendLine("===== Report ${index + 1} / ${reportFiles.size} =====")
            sb.appendLine("File: ${file.name}")
            sb.appendLine("Modified: ${timestampFormat.format(Date(file.lastModified()))}")
            sb.appendLine()
            sb.appendLine(file.safeReadText())
            sb.appendLine()
        }

        return sb.toString()
    }

    fun hasReports(context: Context): Boolean = listReportFiles(context).isNotEmpty()

    fun clearReports(context: Context): Int {
        val files = listReportFiles(context)
        var deleted = 0
        files.forEach { file ->
            if (file.delete()) deleted += 1
        }
        return deleted
    }

    private fun listReportFiles(context: Context): List<File> {
        val dirs = runCatching {
            val locator = ReportLocator(context)
            listOf(locator.approvedFolder, locator.unapprovedFolder)
        }.getOrElse {
            listOf(File(context.filesDir, "ACRA-approved"), File(context.filesDir, "ACRA-unapproved"))
        }

        return dirs
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir ->
                (dir.listFiles()?.asSequence() ?: emptySequence())
                    .filter { it.isFile }
            }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    private fun File.safeReadText(): String {
        return runCatching { readText(Charsets.UTF_8) }
            .getOrElse { "Failed to read crash report file: ${it.message ?: "unknown error"}" }
    }
}
