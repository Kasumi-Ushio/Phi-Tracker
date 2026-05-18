package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.SongInfo

/**
 * Supports wildcard search:
 * - `*` matches one or more non-space characters
 * - `?` matches exactly one non-space character
 * - spaces match one or more whitespace characters
 * - falls back to contains match when no wildcards
 */
class SearchSongUseCase {

    operator fun invoke(
        query: String,
        allSongs: Map<String, SongInfo>
    ): List<SongInfo> {
        if (query.isBlank()) return allSongs.values.toList()

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return allSongs.values.toList()

        // Beta5 guard: queries containing consecutive 2+ '*' (e.g. "**", "***")
        // return all songs immediately to avoid pathological regex expansion
        // from \S+\S+ patterns.
        if (trimmed.contains(Regex("""\*{2,}"""))) {
            return allSongs.values.sortedBy { it.name }
        }

        val hasWildcard = trimmed.contains('*') || trimmed.contains('?')

        return if (hasWildcard) {
            val regex = buildWildcardRegex(trimmed) ?: return emptyList()
            allSongs.values.filter { song ->
                regex.containsMatchIn(song.name) ||
                regex.containsMatchIn(song.id) ||
                regex.containsMatchIn(song.composer)
            }.sortedBy { it.name }
        } else {
            val lowerQuery = trimmed.lowercase()
            allSongs.values.filter { song ->
                song.name.lowercase().contains(lowerQuery) ||
                song.id.lowercase().contains(lowerQuery) ||
                song.composer.lowercase().contains(lowerQuery)
            }.sortedBy { it.name }
        }
    }

    /**
     * Convert wildcard query to regex (partial match):
     * - `*` -> `\S+` (one or more non-whitespace chars)
     * - `?` -> `\S` (exactly one non-whitespace char)
     * - space -> `\s+` (one or more whitespace chars)
     * - other chars escaped
     *
     * Returns null if the pattern cannot be compiled.
     */
    private fun buildWildcardRegex(query: String): Regex? {
        val sb = StringBuilder()
        for (ch in query) {
            when (ch) {
                '*' -> sb.append("\\S+")
                '?' -> sb.append("\\S")
                ' ' -> sb.append("\\s+")
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        return try {
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            null
        }
    }
}
