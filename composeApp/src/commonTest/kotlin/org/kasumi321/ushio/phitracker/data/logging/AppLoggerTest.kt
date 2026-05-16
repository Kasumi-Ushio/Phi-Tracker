package org.kasumi321.ushio.phitracker.data.logging

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLoggerTest {

    private val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "app_logger_test_${kotlin.random.Random.nextInt(10000, 99999)}"

    @AfterTest
    fun tearDown() {
        try {
            if (FileSystem.SYSTEM.exists(tempDir)) {
                FileSystem.SYSTEM.deleteRecursively(tempDir)
            }
        } catch (e: Exception) {
            println("AppLoggerTest cleanup failed: ${e.message}")
        }
    }

    @Test
    fun `event method outputs readable text format with attributes`() {
        val runtimeDir = tempDir / "runtime"
        val crashDir = tempDir / "crash"

        val store = LogFileStore(
            fileSystem = FileSystem.SYSTEM,
            runtimeLogDir = runtimeDir,
            crashLogDir = crashDir
        )

        AppLogger.init(LoggerConfig(enableFileWriter = true, logFileStore = store))
        assertTrue(AppLogger.fileWriterActive)

        AppLogger.event("init", "logging", mapOf("fileWriter" to "true"))
        AppLogger.event("navigation", "entered_home")

        val runtimeFiles = store.listRuntimeFiles()
        assertTrue(runtimeFiles.isNotEmpty())

        val export = store.buildRuntimeExport()
        assertContains(export, "[event] category=init name=logging fileWriter=true")
        assertContains(export, "[event] category=navigation name=entered_home")
    }

    @Test
    fun `event method works without file writer`() {
        val runtimeDir = tempDir / "runtime_nofile"
        val crashDir = tempDir / "crash_nofile"

        val store = LogFileStore(
            fileSystem = FileSystem.SYSTEM,
            runtimeLogDir = runtimeDir,
            crashLogDir = crashDir
        )

        AppLogger.init(LoggerConfig(enableFileWriter = false, logFileStore = store))
        AppLogger.event("init", "koin", mapOf("status" to "success"))
        AppLogger.event("api", "test_started")
    }

    @Test
    fun `redacts sessionToken and preserves tokenPresent in event export`() {
        val runtimeDir = tempDir / "runtime_redact"
        val crashDir = tempDir / "crash_redact"
        val store = LogFileStore(FileSystem.SYSTEM, runtimeDir, crashDir)
        AppLogger.init(LoggerConfig(enableFileWriter = true, logFileStore = store))
        assertTrue(AppLogger.fileWriterActive)

        AppLogger.event("auth", "login", mapOf(
            "sessionToken" to "r_abc123secret",
            "tokenPresent" to "true",
            "platformId" to "android-test",
        ))

        val export = store.buildRuntimeExport()
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "tokenPresent=true")
        assertContains(export, "platformId=<redacted>")
        assertFalse(export.contains("r_abc123secret"))
        assertFalse(export.contains("android-test"))
    }

    @Test
    fun `redacts sensitive data in direct log messages through file writer`() {
        val runtimeDir = tempDir / "runtime_direct"
        val crashDir = tempDir / "crash_direct"
        val store = LogFileStore(FileSystem.SYSTEM, runtimeDir, crashDir)
        AppLogger.init(LoggerConfig(enableFileWriter = true, logFileStore = store))

        AppLogger.i("TestTag", "Auth failed: sessionToken=secret123 X-LC-Session: abc456")
        AppLogger.i("TestTag", "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload")

        val export = store.buildRuntimeExport()
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "X-LC-Session: <redacted>")
        assertContains(export, "Authorization: Bearer <redacted>")
        assertFalse(export.contains("secret123"))
        assertFalse(export.contains("abc456"))
        assertFalse(export.contains("eyJhbGci"))
    }

    @Test
    fun `redacts access_token and mac_key in events`() {
        val runtimeDir = tempDir / "runtime_tokens"
        val crashDir = tempDir / "crash_tokens"
        val store = LogFileStore(FileSystem.SYSTEM, runtimeDir, crashDir)
        AppLogger.init(LoggerConfig(enableFileWriter = true, logFileStore = store))

        AppLogger.event("oauth", "token_refresh", mapOf(
            "access_token" to "ya29.secret123",
            "mac_key" to "deadbeef-cafe-9999",
        ))

        val export = store.buildRuntimeExport()
        assertContains(export, "access_token=<redacted>")
        assertContains(export, "mac_key=<redacted>")
        assertFalse(export.contains("ya29.secret123"))
        assertFalse(export.contains("deadbeef-cafe-9999"))
    }

    @Test
    fun `retains non-sensitive attributes unchanged in export`() {
        val runtimeDir = tempDir / "runtime_safe"
        val crashDir = tempDir / "crash_safe"
        val store = LogFileStore(FileSystem.SYSTEM, runtimeDir, crashDir)
        AppLogger.init(LoggerConfig(enableFileWriter = true, logFileStore = store))

        AppLogger.event("ui", "button_click", mapOf(
            "button" to "export_logs",
            "screen" to "settings",
        ))

        val export = store.buildRuntimeExport()
        assertContains(export, "button=export_logs")
        assertContains(export, "screen=settings")
    }

    @Test
    fun `buildRuntimeExport redacts raw pre-existing runtime file`() {
        val runtimeDir = tempDir / "runtime_raw"
        val crashDir = tempDir / "crash_raw"
        val fs = FileSystem.SYSTEM
        if (!fs.exists(runtimeDir)) fs.createDirectories(runtimeDir)
        if (!fs.exists(crashDir)) fs.createDirectories(crashDir)

        val rawContent = """
            2026-05-16 12:00:00.000 I/OldLogger: Auth failed: sessionToken=leakedSecret999
            2026-05-16 12:00:01.000 I/OldLogger: X-LC-Session: leaked-session-hdr
            2026-05-16 12:00:02.000 I/OldLogger: platformId=old-android-test
            2026-05-16 12:00:03.000 I/OldLogger: access_token=ya29.leaked_oauth
        """.trimIndent()
        val rawPath = runtimeDir / "runtime_old_raw.txt"
        fs.write(rawPath) { writeUtf8(rawContent) }

        val store = LogFileStore(fs, runtimeDir, crashDir)
        assertTrue(store.hasRuntimeLogs())

        val export = store.buildRuntimeExport()
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "X-LC-Session: <redacted>")
        assertContains(export, "platformId=<redacted>")
        assertContains(export, "access_token=<redacted>")
        assertFalse(export.contains("leakedSecret999"))
        assertFalse(export.contains("leaked-session-hdr"))
        assertFalse(export.contains("old-android-test"))
        assertFalse(export.contains("ya29.leaked_oauth"))
    }

    @Test
    fun `buildCrashExport redacts raw pre-existing crash file`() {
        val runtimeDir = tempDir / "runtime_crash_raw"
        val crashDir = tempDir / "crash_crash_raw"
        val fs = FileSystem.SYSTEM
        if (!fs.exists(runtimeDir)) fs.createDirectories(runtimeDir)
        if (!fs.exists(crashDir)) fs.createDirectories(crashDir)

        val rawCrash = """
            === Kotlin Uncaught Exception ===
            Time: 2026-05-16 12:05:00
            Thread: main
            Exception: java.lang.RuntimeException: API call failed with sessionToken=crashLeak123
            Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.crash_token_value
            mac_key=dead-beef-crash-key
            at com.example.Api.call(Api.kt:100)
            Caused by (1): java.io.IOException: Bearer leakedBearerInCause
        """.trimIndent()
        val crashPath = crashDir / "crash_old_raw.txt"
        fs.write(crashPath) { writeUtf8(rawCrash) }

        val store = LogFileStore(fs, runtimeDir, crashDir)
        assertTrue(store.hasCrashLogs())

        val export = store.buildCrashExport()
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "Authorization: Bearer <redacted>")
        assertContains(export, "mac_key=<redacted>")
        assertContains(export, "Bearer <redacted>")
        assertFalse(export.contains("crashLeak123"))
        assertFalse(export.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(export.contains("dead-beef-crash-key"))
        assertFalse(export.contains("leakedBearerInCause"))
    }
}
