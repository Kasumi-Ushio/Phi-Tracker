package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.chapter_other

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
 * "--" placeholder carried by chapter-less songs reads as [otherLabel].
 * Filtering keeps matching on the raw value; this is a label-only mapping.
 *
 * The [otherLabel] default is a non-composable fallback for tests and other
 * non-composable callers; composable UI should use
 * [chapterDisplayNameLocalized] so the placeholder is resolved from string
 * resources.
 */
fun chapterDisplayName(chapter: String, otherLabel: String = "其它"): String = when {
    chapter == CHAPTER_PLACEHOLDER -> otherLabel
    chapter.startsWith(COLLAB_CHAPTER_PREFIX) -> chapter.removePrefix(COLLAB_CHAPTER_PREFIX)
    chapter.startsWith(MAIN_STORY_PREFIX) &&
        chapter.getOrNull(MAIN_STORY_PREFIX.length)?.isDigit() != true ->
        chapter.removePrefix(MAIN_STORY_PREFIX)
    else -> chapter
}

/**
 * Localized variant of [chapterDisplayName] for composable callers: resolves
 * the chapter-less placeholder from `Res.string.chapter_other`.
 */
@Composable
fun chapterDisplayNameLocalized(chapter: String): String =
    chapterDisplayName(chapter, otherLabel = stringResource(Res.string.chapter_other))
