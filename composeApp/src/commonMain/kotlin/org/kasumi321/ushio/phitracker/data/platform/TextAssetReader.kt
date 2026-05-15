package org.kasumi321.ushio.phitracker.data.platform

import okio.FileSystem
import okio.Path.Companion.toPath

interface TextAssetReader {
    fun readText(name: String): String
}

expect fun createTextAssetReader(): TextAssetReader

/**
 * Creates a [TextAssetReader] that first attempts to read [name] from
 * `filesDir/song_data/<name>` on the local file-system, and falls back to
 * the bundled [assetReader] when the file does not exist.
 */
fun createFileThenAssetReader(
    assetReader: TextAssetReader,
    paths: PlatformPaths
): TextAssetReader = FileThenAssetReader(assetReader, paths)

private class FileThenAssetReader(
    private val assetReader: TextAssetReader,
    private val paths: PlatformPaths
) : TextAssetReader {

    private val fs: FileSystem = FileSystem.SYSTEM
    private val songDataDir by lazy { paths.filesDir.toPath() / "song_data" }

    override fun readText(name: String): String {
        val filePath = songDataDir / name
        if (fs.exists(filePath)) {
            return fs.read(filePath) { readUtf8() }
        }
        return assetReader.readText(name)
    }
}
