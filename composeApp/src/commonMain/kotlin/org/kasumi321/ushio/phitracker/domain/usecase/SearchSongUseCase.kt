package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.SongInfo

class SearchSongUseCase {
    operator fun invoke(
        query: String,
        allSongs: Map<String, SongInfo>
    ): List<SongInfo> {
        if (query.isBlank()) return allSongs.values.toList()

        val lowerQuery = query.lowercase()
        return allSongs.values.filter { song ->
            song.name.lowercase().contains(lowerQuery) ||
                song.id.lowercase().contains(lowerQuery) ||
                song.composer.lowercase().contains(lowerQuery)
        }.sortedBy { it.name }
    }
}
