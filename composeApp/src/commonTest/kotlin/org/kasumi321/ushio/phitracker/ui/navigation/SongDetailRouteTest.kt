package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongDetailRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- basic serialization ----------------------------------------------------

    @Test
    fun basicSongIdSurvivesSerialization() {
        val route = SongDetailRoute("TestSong123")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    // ---- non-ASCII (Chinese / Japanese) -----------------------------------------

    @Test
    fun chineseSongIdSurvivesSerialization() {
        val route = SongDetailRoute("光.姜米條.0")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun japaneseSongIdSurvivesSerialization() {
        val route = SongDetailRoute("もぺもぺ.LeaF.0")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun mixedScriptSongIdSurvivesSerialization() {
        val route = SongDetailRoute("混乱Confusion.OnlyMyBlackScore.0")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    // ---- reserved characters ----------------------------------------------------

    @Test
    fun reservedCharactersSurviveSerialization() {
        val route = SongDetailRoute("a/b?c#d e&f=g%h+i")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    // ---- real-world song IDs from Phigros ---------------------------------------

    @Test
    fun allProblematicSongIdsSurviveSerialization() {
        val ids = listOf(
            "光.姜米條.0",
            "もぺもぺ.LeaF.0",
            "混乱Confusion.OnlyMyBlackScore.0",
            "a/b/c",
            "a?b#c",
            "a b&c=d",
            "100%",
            "hello+world",
            "test.id.with.dots",
            "~tilde.allowed"
        )
        for (id in ids) {
            val route = SongDetailRoute(id)
            val encoded = json.encodeToString(route)
            val decoded = json.decodeFromString<SongDetailRoute>(encoded)
            assertEquals(route, decoded, "Serialization round-trip failed for '$id'")
        }
    }

    // ---- edge cases -------------------------------------------------------------

    @Test
    fun emptyStringSongIdSurvivesSerialization() {
        val route = SongDetailRoute("")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun dotOnlySurvivesSerialization() {
        val route = SongDetailRoute("...")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun tildeOnlySurvivesSerialization() {
        val route = SongDetailRoute("~test~")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
    }

    // ---- Phase C: difficulty parameter ----

    @Test
    fun routeWithNullDifficultySerializes() {
        val route = SongDetailRoute("song-a", difficulty = null)
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<SongDetailRoute>(encoded)
        assertEquals(route, decoded)
        assertNull(decoded.difficulty)
    }

    @Test
    fun routeWithDifficultySerializes() {
        for (diff in Difficulty.values()) {
            val route = SongDetailRoute("song-a", difficulty = diff)
            val encoded = json.encodeToString(route)
            val decoded = json.decodeFromString<SongDetailRoute>(encoded)
            assertEquals(route, decoded, "Failed for difficulty $diff")
            assertEquals(diff, decoded.difficulty)
        }
    }

    @Test
    fun defaultDifficultyIsNull() {
        val route = SongDetailRoute("song-a")
        assertNull(route.difficulty)
    }
}
