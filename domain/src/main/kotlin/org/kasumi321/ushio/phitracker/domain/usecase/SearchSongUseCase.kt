package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import javax.inject.Inject

/**
 * 曲目搜索用例
 */
class SearchSongUseCase @Inject constructor() {

    /**
     * 按名称或 ID 模糊搜索曲目
     */
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
