package org.kasumi321.ushio.phitracker.utils

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * Timber 运行日志采集（Debug only）:
 * - 继续输出到 Logcat（通过 DebugTree）
 * - 同步写入本地文件，供用户手动导出
 */
object RuntimeLogCollector {

    private const val LOG_DIR = "runtime_logs"
    private const val CURRENT_LOG_NAME = "timber_current.txt"
    private const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L
    private const val MAX_ROTATED_FILES = 5

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun createTree(context: Context): Timber.Tree = RuntimeFileTree(context.applicationContext)

    fun listLogFiles(context: Context): List<File> {
        val dir = logDir(context)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return (dir.listFiles()?.asSequence() ?: emptySequence())
            .filter { it.isFile && it.name.startsWith("timber_") }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    fun clearLogs(context: Context): Int {
        val files = listLogFiles(context)
        var deleted = 0
        files.forEach { file ->
            if (file.delete()) deleted += 1
        }
        return deleted
    }

    private fun currentLogFile(context: Context): File = File(logDir(context), CURRENT_LOG_NAME)

    private fun logDir(context: Context): File = File(context.filesDir, LOG_DIR)

    private fun openWriter(file: File, append: Boolean): BufferedWriter {
        return BufferedWriter(OutputStreamWriter(FileOutputStream(file, append), Charsets.UTF_8))
    }

    private fun rotateLogs(dir: File, current: File) {
        if (!current.exists()) return

        for (i in MAX_ROTATED_FILES - 1 downTo 1) {
            val src = File(dir, "timber_$i.txt")
            if (src.exists()) {
                val dest = File(dir, "timber_${i + 1}.txt")
                src.renameTo(dest)
            }
        }

        val first = File(dir, "timber_1.txt")
        current.renameTo(first)
        current.createNewFile()

        val files = (dir.listFiles()?.toList() ?: emptyList())
            .filter { it.name.matches(Regex("""timber_\d+\.txt""")) }
            .sortedByDescending { it.name }
        files.drop(MAX_ROTATED_FILES).forEach { it.delete() }
    }

    private class RuntimeFileTree(
        private val context: Context
    ) : Timber.DebugTree() {

        private val lock = Any()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, tag, message, t) // keep Logcat behavior
            appendToFile(priority, tag, message, t)
        }

        private fun appendToFile(priority: Int, tag: String?, message: String, t: Throwable?) {
            synchronized(lock) {
                val dir = logDir(context)
                if (!dir.exists()) dir.mkdirs()

                val current = currentLogFile(context)
                if (!current.exists()) current.createNewFile()
                if (current.length() >= MAX_FILE_SIZE_BYTES) {
                    rotateLogs(dir, current)
                }

                val sanitizedMessage = LogSanitizer.sanitize(message)
                val sanitizedStacktrace = t?.let { LogSanitizer.sanitize(Log.getStackTraceString(it)) }
                openWriter(current, append = true).use { writer ->
                    writer.appendLine(
                        "${timestampFormat.format(Date())} ${priorityToChar(priority)}/${tag ?: "PhiTracker"}: $sanitizedMessage"
                    )
                    sanitizedStacktrace?.let { writer.appendLine(it) }
                }
            }
        }

        private fun priorityToChar(priority: Int): Char = when (priority) {
            Log.VERBOSE -> 'V'
            Log.DEBUG -> 'D'
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR -> 'E'
            Log.ASSERT -> 'A'
            else -> '?'
        }
    }
}
