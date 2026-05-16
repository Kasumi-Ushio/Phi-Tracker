package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig

object AppLogger {

    private var _logger: Logger? = null
    val logger: Logger get() = _logger ?: Logger(StaticConfig())

    var fileWriterActive: Boolean = false
        private set

    fun init(config: LoggerConfig) {
        val writers = mutableListOf<co.touchlab.kermit.LogWriter>()
        fileWriterActive = false
        if (config.enableFileWriter) {
            writers.add(FileLogWriter(config.logFileStore))
            fileWriterActive = true
        }
        _logger = Logger(StaticConfig(logWriterList = writers))
        _logger!!.i(messageString = "Logging system initialised, fileWriter=${config.enableFileWriter}", tag = "AppLogger")
        event("init", "logging", mapOf("fileWriter" to config.enableFileWriter.toString()))
    }

    fun event(category: String, name: String, attributes: Map<String, String> = emptyMap()) {
        val attrStr = if (attributes.isEmpty()) {
            ""
        } else {
            " " + attributes.entries.joinToString(" ") { (key, value) ->
                "${sanitizeKey(key)}=${sanitizeValue(key, value)}"
            }
        }
        val message = LogRedactor.redact("[event] category=${sanitizeKey(category)} name=${sanitizeKey(name)}$attrStr")
        logger.i(message, tag = "Event")
    }

    private fun sanitizeKey(key: String): String = key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun sanitizeValue(key: String, value: String): String {
        val lowerKey = key.lowercase()
        if (lowerKey.endsWith("present") && (value == "true" || value == "false")) {
            return value
        }
        if (lowerKey.contains("token") || lowerKey.contains("platformid") || lowerKey.contains("credential")) {
            return "<redacted>"
        }
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(120)
    }

    fun i(tag: String, message: String) = logger.i(message, tag = tag)

    fun d(tag: String, message: String) = logger.d(message, tag = tag)

    fun w(tag: String, message: String) = logger.w(message, tag = tag)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        logger.e(message, throwable, tag = tag)
}

data class LoggerConfig(
    val enableFileWriter: Boolean,
    val logFileStore: LogFileStore,
)
