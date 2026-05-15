package org.kasumi321.ushio.phitracker.data.song

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createFileThenAssetReader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongDataProviderTest {

    private val fs = FileSystem.SYSTEM
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var testBaseDir: okio.Path
    private lateinit var paths: PlatformPaths
    private lateinit var bundledAssets: Map<String, String>

    @BeforeTest
    fun setUp() {
        testBaseDir = "/tmp/song-provider-test-${hashCode()}".toPath()
        fs.createDirectories(testBaseDir)
        fs.createDirectories(testBaseDir / "cache")

        paths = PlatformPaths(
            filesDir = testBaseDir.toString(),
            cacheDir = (testBaseDir / "cache").toString()
        )

        bundledAssets = mapOf(
            "difficulty.csv" to """
                |id,EZ,HD,IN,AT
                |test,1.0,2.0,3.0,
            """.trimMargin(),
            "info.csv" to """
                |id,name,composer,illustrator,ez_charter,hd_charter,in_charter,at_charter
                |test,BundledName,BundledComposer,BundledIllustrator,,,,
            """.trimMargin(),
            "infolist.json" to """{}""",
            "notesInfo.json" to """{}"""
        )
    }

    @AfterTest
    fun tearDown() {
        if (::testBaseDir.isInitialized) {
            fs.deleteRecursively(testBaseDir)
        }
    }

    private fun fakeReader(): TextAssetReader = object : TextAssetReader {
        override fun readText(name: String): String =
            bundledAssets[name] ?: error("Asset not found: $name")
    }

    private fun writeSongDataFile(name: String, content: String) {
        val dir = testBaseDir / "song_data"
        fs.createDirectories(dir)
        fs.write(dir / name) { writeUtf8(content) }
    }

    @Test
    fun readsFromBundleWhenNoOverrideFileExists() {
        val provider = SongDataProvider(
            assetReader = fakeReader(),
            paths = paths,
            json = json
        )
        val songs = provider.getSongs()
        assertEquals(1, songs.size)
        assertEquals("BundledName", songs["test.0"]?.name)
    }

    @Test
    fun readsFromFileOverrideWhenItExists() {
        writeSongDataFile(
            "difficulty.csv",
            """
                |id,EZ,HD,IN,AT
                |test,9.0,9.5,10.0,10.5
            """.trimMargin()
        )

        val provider = SongDataProvider(
            assetReader = fakeReader(),
            paths = paths,
            json = json
        )
        val songs = provider.getSongs()
        assertEquals(1, songs.size)

        val diffs = songs["test.0"]?.difficulties ?: error("No song")
        assertEquals(9.0f, diffs[org.kasumi321.ushio.phitracker.domain.model.Difficulty.EZ])
        assertEquals(9.5f, diffs[org.kasumi321.ushio.phitracker.domain.model.Difficulty.HD])
        assertEquals(10.0f, diffs[org.kasumi321.ushio.phitracker.domain.model.Difficulty.IN])
    }

    @Test
    fun bundledFallbackStillWorksAfterOverrideForDifferentFile() {
        writeSongDataFile(
            "info.csv",
            """
                |id,name,composer,illustrator,ez_charter,hd_charter,in_charter,at_charter
                |test,OverriddenName,OverriddenComposer,OverriddenIllustrator,,,,
            """.trimMargin()
        )

        val provider = SongDataProvider(
            assetReader = fakeReader(),
            paths = paths,
            json = json
        )
        val songs = provider.getSongs()
        assertEquals("OverriddenName", songs["test.0"]?.name)
        assertEquals("OverriddenComposer", songs["test.0"]?.composer)
    }

    @Test
    fun invalidateCacheReReadsData() {
        val provider = SongDataProvider(
            assetReader = fakeReader(),
            paths = paths,
            json = json
        )

        val first = provider.getSongs()
        assertEquals("BundledName", first["test.0"]?.name)

        writeSongDataFile(
            "info.csv",
            """
                |id,name,composer,illustrator,ez_charter,hd_charter,in_charter,at_charter
                |test,UpdatedName,UpdatedComposer,UpdatedIllustrator,,,,
            """.trimMargin()
        )

        provider.invalidateCache()
        val second = provider.getSongs()
        assertEquals("UpdatedName", second["test.0"]?.name)
    }

    @Test
    fun createFileThenAssetReaderReadsFileFirst() {
        writeSongDataFile("test.txt", "file-content")

        val reader = createFileThenAssetReader(fakeReader(), paths)
        val result = reader.readText("test.txt")

        assertEquals("file-content", result)
    }

    @Test
    fun createFileThenAssetReaderFallsBackToAsset() {
        val reader = createFileThenAssetReader(fakeReader(), paths)
        val result = reader.readText("difficulty.csv")

        assertEquals(bundledAssets["difficulty.csv"], result)
    }
}
