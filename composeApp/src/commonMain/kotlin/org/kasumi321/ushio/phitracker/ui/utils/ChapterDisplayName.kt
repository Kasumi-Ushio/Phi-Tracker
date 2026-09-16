package org.kasumi321.ushio.phitracker.ui.utils

private const val CHAPTER_PLACEHOLDER = "--"
private const val MAIN_STORY_PREFIX = "Chapter "
private const val COLLAB_CHAPTER_PREFIX = "Chapter EX-"

/**
 * Shortens the raw chapter string from infolist.json for display.
 *
 * Main story chapters keep their prefix ("Chapter 5 霓虹灯牌") and side
 * stories are untouched ("Side Story 1 忘忧宫"); every other "Chapter "
 * prefix is dropped, and collaboration chapters additionally lose their
 * "EX-" marker ("Chapter EX-CHUNITHM 精选集" -> "CHUNITHM 精选集"). The
 * "--" placeholder carried by chapter-less songs reads as "其它".
 * Filtering keeps matching on the raw value; this is a label-only mapping.
 */
fun chapterDisplayName(chapter: String): String = when {
    chapter == CHAPTER_PLACEHOLDER -> "其它"
    chapter.startsWith(COLLAB_CHAPTER_PREFIX) -> chapter.removePrefix(COLLAB_CHAPTER_PREFIX)
    chapter.startsWith(MAIN_STORY_PREFIX) &&
        chapter.getOrNull(MAIN_STORY_PREFIX.length)?.isDigit() != true ->
        chapter.removePrefix(MAIN_STORY_PREFIX)
    else -> chapter
}
