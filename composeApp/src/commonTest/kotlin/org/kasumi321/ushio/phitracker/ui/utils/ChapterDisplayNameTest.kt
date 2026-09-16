package org.kasumi321.ushio.phitracker.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterDisplayNameTest {

    @Test
    fun mainStoryChapterKeepsPrefix() {
        assertEquals("Chapter 5 霓虹灯牌", chapterDisplayName("Chapter 5 霓虹灯牌"))
        assertEquals("Chapter 8 凌日潮汐", chapterDisplayName("Chapter 8 凌日潮汐"))
    }

    @Test
    fun sideStoryIsUntouched() {
        assertEquals("Side Story 1 忘忧宫", chapterDisplayName("Side Story 1 忘忧宫"))
        assertEquals("Side Story 4 无相乡", chapterDisplayName("Side Story 4 无相乡"))
    }

    @Test
    fun collaborationChapterDropsChapterAndExPrefix() {
        assertEquals("CHUNITHM 精选集", chapterDisplayName("Chapter EX-CHUNITHM 精选集"))
        assertEquals("Paradigm：Reboot 精选集", chapterDisplayName("Chapter EX-Paradigm：Reboot 精选集"))
        assertEquals("茶鸣拾贰律 精选集", chapterDisplayName("Chapter EX-茶鸣拾贰律 精选集"))
    }

    @Test
    fun legacyChapterDropsChapterPrefixOnly() {
        assertEquals("Legacy 过去的章节", chapterDisplayName("Chapter Legacy 过去的章节"))
    }

    @Test
    fun otherChapterFormsAreUntouched() {
        assertEquals("Extra Story Chapter 极星卫", chapterDisplayName("Extra Story Chapter 极星卫"))
        assertEquals("Single-单曲 精选集", chapterDisplayName("Single-单曲 精选集"))
        assertEquals("单曲精选集", chapterDisplayName("单曲精选集"))
    }

    @Test
    fun placeholderReadsAsOther() {
        assertEquals("其它", chapterDisplayName("--"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", chapterDisplayName(""))
    }
}
