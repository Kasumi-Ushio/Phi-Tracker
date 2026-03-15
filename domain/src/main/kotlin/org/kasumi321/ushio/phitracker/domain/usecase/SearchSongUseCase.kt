package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import javax.inject.Inject

/**
 * 曲目搜索用例
 *
 * 支持通配符搜索：
 * - `*` 匹配一个或多个非空格字符
 * - `?` 匹配恰好一个非空格字符
 * - 空格保留为字面空格
 * - 无通配符时回退到普通包含匹配
 */
class SearchSongUseCase @Inject constructor() {

    operator fun invoke(
        query: String,
        allSongs: Map<String, SongInfo>
    ): List<SongInfo> {
        if (query.isBlank()) return allSongs.values.toList()

        val hasWildcard = query.contains('*') || query.contains('?')

        return if (hasWildcard) {
            val regex = buildWildcardRegex(query)
            allSongs.values.filter { song ->
                regex.matches(song.name) ||
                regex.matches(song.id) ||
                regex.matches(song.composer)
            }.sortedBy { it.name }
        } else {
            val lowerQuery = query.lowercase()
            allSongs.values.filter { song ->
                song.name.lowercase().contains(lowerQuery) ||
                song.id.lowercase().contains(lowerQuery) ||
                song.composer.lowercase().contains(lowerQuery)
            }.sortedBy { it.name }
        }
    }

    /**
     * 将通配符查询转为正则：
     * - `*` → `[^\s]+`（至少一个非空格字符）
     * - `?` → `[^\s]`（恰好一个非空格字符）
     * - 空格 → `\s`
     * - 其他字符转义
     */
    private fun buildWildcardRegex(query: String): Regex {
        val sb = StringBuilder("^")
        for (ch in query) {
            when (ch) {
                '*' -> sb.append("[^\\s]+")
                '?' -> sb.append("[^\\s]")
                ' ' -> sb.append("\\s")
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
