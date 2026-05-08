package org.kasumi321.ushio.phitracker.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteCount(
    val tap: Int = 0,
    val drag: Int = 0,
    val hold: Int = 0,
    val flick: Int = 0
) {
    val total get() = tap + drag + hold + flick
}

@Serializable
data class SongInfo(
    val id: String,
    val name: String,
    val composer: String,
    val illustrator: String,
    val difficulties: Map<Difficulty, Float>,
    val bpm: String = "",
    val chapter: String = "",
    val length: String = "",
    val charters: Map<Difficulty, String> = emptyMap(),
    val noteCounts: Map<Difficulty, NoteCount> = emptyMap()
)
