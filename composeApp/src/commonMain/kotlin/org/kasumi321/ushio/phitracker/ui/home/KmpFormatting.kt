package org.kasumi321.ushio.phitracker.ui.home

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Float.formatFour(): String {
    val scaled = (this * 10_000f).roundToLong()
    val whole = abs(scaled) / 10_000L
    val fraction = (abs(scaled) % 10_000L).toString().padStart(4, '0')
    val sign = if (scaled < 0) "-" else ""
    return "$sign$whole.$fraction"
}

fun Float.formatTwo(): String {
    val scaled = (this * 100f).roundToLong()
    val whole = abs(scaled) / 100L
    val fraction = (abs(scaled) % 100L).toString().padStart(2, '0')
    val sign = if (scaled < 0) "-" else ""
    return "$sign$whole.$fraction"
}

fun Float.formatOne(): String {
    val scaled = (this * 10f).roundToLong()
    val whole = abs(scaled) / 10L
    val fraction = (abs(scaled) % 10L).toString()
    val sign = if (scaled < 0) "-" else ""
    return "$sign$whole.$fraction"
}

fun epochMillisToDateTimeString(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.year}-${(dt.month.ordinal + 1).pad2()}-${dt.day.pad2()} ${dt.hour.pad2()}:${dt.minute.pad2()}:${dt.second.pad2()}"
}

fun epochMillisToShortDateString(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${(dt.month.ordinal + 1).pad2()}-${dt.day.pad2()} ${dt.hour.pad2()}:${dt.minute.pad2()}"
}

private fun Int.pad2(): String = toString().padStart(2, '0')
