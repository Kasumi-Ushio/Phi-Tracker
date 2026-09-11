package org.kasumi321.ushio.phitracker.ui.b30

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun resolveAutoBackground(
    exportData: B30ExportData,
    standardIllustrationProvider: (String) -> String,
    autoSongId: String?
): String? {
    // The pool is exactly the records counting toward B30 (phi + best 27);
    // overflow records are excluded.
    val pool = exportData.phiRecords + exportData.bestRecords
    if (pool.isEmpty()) return null
    // [autoSongId] carries the screen's per-session roll so that regenerations
    // (style/blur changes) keep the same background song; a null or stale id
    // (record no longer in the pool) falls back to a fresh random pick.
    val chosen = autoSongId?.let { id -> pool.firstOrNull { it.record.songId == id } }
        ?: pool.random()
    val uri = standardIllustrationProvider(chosen.record.songId)
    // The picked song may lack an illustration; fall back to the first
    // resolvable one so Auto never needlessly yields a background-less image.
    return uri.takeIf { it.isNotBlank() } ?: pool.asSequence()
        .map { standardIllustrationProvider(it.record.songId) }
        .firstOrNull { it.isNotBlank() }
}

fun sanitizeNicknameForFilename(nickname: String): String {
    val trimmed = nickname.trim()
    val base = if (trimmed.isBlank()) "Unknown" else trimmed
    val capped = base.take(24)
    return capped.replace(INVALID_FILENAME_CHARS, "_")
}

private val INVALID_FILENAME_CHARS = Regex("[/\\\\:*?\"<>|\\x00-\\x1F\\x7F]")

@OptIn(ExperimentalTime::class)
fun buildB30ExportFilename(
    nickname: String,
    instant: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.UTC
): String {
    val safe = sanitizeNicknameForFilename(nickname)
    val dt = instant.toLocalDateTime(timeZone)
    val timestamp = "${dt.year.toString().padStart(4, '0')}-" +
        "${(dt.month.ordinal + 1).toString().padStart(2, '0')}-" +
        "${dt.day.toString().padStart(2, '0')}-" +
        "${dt.hour.toString().padStart(2, '0')}-" +
        "${dt.minute.toString().padStart(2, '0')}-" +
        "${dt.second.toString().padStart(2, '0')}"
    return "PhiTracker-B30-$safe-$timestamp.png"
}

sealed interface B30BackgroundMode {
    data object Auto : B30BackgroundMode
    data class SongBackground(val songId: String) : B30BackgroundMode
    data class Custom(val uri: String) : B30BackgroundMode
}

fun resolveBackgroundUri(
    mode: B30BackgroundMode,
    exportData: B30ExportData,
    standardIllustrationProvider: (String) -> String,
    autoSongId: String? = null
): String? = when (mode) {
    is B30BackgroundMode.Custom -> mode.uri
    is B30BackgroundMode.SongBackground -> {
        standardIllustrationProvider(mode.songId)
    }
    B30BackgroundMode.Auto -> resolveAutoBackground(exportData, standardIllustrationProvider, autoSongId)
}
