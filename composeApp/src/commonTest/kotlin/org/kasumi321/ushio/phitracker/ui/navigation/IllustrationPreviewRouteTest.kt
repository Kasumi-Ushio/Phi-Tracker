package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class IllustrationPreviewRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun basicSongIdSurvivesSerialization() {
        val route = IllustrationPreviewRoute.from("TestSong123")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<IllustrationPreviewRoute>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun nonAsciiSongIdSurvivesSerialization() {
        for (id in listOf("光.姜米條.0", "もぺもぺ.LeaF.0", "混乱Confusion.OnlyMyBlackScore.0")) {
            val route = IllustrationPreviewRoute.from(id)
            val encoded = json.encodeToString(route)
            val decoded = json.decodeFromString<IllustrationPreviewRoute>(encoded)
            assertEquals(route, decoded, "Serialization round-trip failed for '$id'")
        }
    }

    @Test
    fun reservedCharactersSurviveSerialization() {
        val route = IllustrationPreviewRoute.from("a/b?c#d e&f=g%h+i")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<IllustrationPreviewRoute>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun edgeCaseSongIdsSurviveSerialization() {
        for (id in listOf("", "...", "~test~", "100%")) {
            val route = IllustrationPreviewRoute.from(id)
            val encoded = json.encodeToString(route)
            val decoded = json.decodeFromString<IllustrationPreviewRoute>(encoded)
            assertEquals(route, decoded, "Serialization round-trip failed for '$id'")
        }
    }
}
