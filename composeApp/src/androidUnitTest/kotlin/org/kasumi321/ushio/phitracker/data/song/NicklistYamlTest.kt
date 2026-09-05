package org.kasumi321.ushio.phitracker.data.song

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Guards the real bundled nicklist.yaml: it must stay parseable by kaml and
 * the provider must attach its aliases to songs keyed by the raw song id
 * (without the ".0" suffix).
 *
 * Reads the resource straight from the source tree via the phitracker.projectDir
 * system property (set for Gradle Test tasks). Lives in androidUnitTest because
 * java.lang.System is unavailable in the iOS test klibrary.
 */
class NicklistYamlTest {

    private val filesDir: okio.Path? = System.getProperty("phitracker.projectDir")
        ?.let { it.toPath() / "composeApp/src/commonMain/composeResources/files" }

    @Test
    fun bundledNicklistYamlParsesWithKaml() {
        val dir = filesDir ?: return
        val content = FileSystem.SYSTEM.read(dir / "nicklist.yaml") { readUtf8() }
        val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
        val nicklist = Yaml().decodeFromString(serializer, content)
        assertTrue(nicklist.isNotEmpty(), "Expected a non-empty nicklist")
        assertTrue(nicklist.values.all { it.isNotEmpty() }, "Expected every nicklist entry to have at least one alias")
    }

    @Test
    fun providerAttachesBundledNicknamesToSongs() {
        val dir = filesDir ?: return
        val reader = object : TextAssetReader {
            override fun readText(name: String): String = FileSystem.SYSTEM.read(dir / name) { readUtf8() }
        }
        val provider = SongDataProvider(assetReader = reader)
        val songs = provider.getSongs()
        val glaciaxion = songs["Glaciaxion.SunsetRay.0"] ?: error("Glaciaxion missing from bundled info.csv")
        assertContains(glaciaxion.nicknames, "Glacier")
        val hikari = songs["光.姜米條.0"] ?: error("光 missing from bundled info.csv")
        assertContains(hikari.nicknames, "Hikari")
    }
}
