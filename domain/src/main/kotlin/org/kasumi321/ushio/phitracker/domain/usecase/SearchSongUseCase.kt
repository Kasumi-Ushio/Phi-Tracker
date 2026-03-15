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

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return allSongs.values.toList()

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
     * 将通配符查询转为正则（部分匹配）：
     * - `*` → `\S+`（至少一个非空白字符）
     * - `?` → `\S`（恰好一个非空白字符）
     * - 空格 → `\s+`（一个或多个空白字符）
     * - 其他字符转义
     *
     * 返回 null 表示无法编译（用户输入了不合法的模式）
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
