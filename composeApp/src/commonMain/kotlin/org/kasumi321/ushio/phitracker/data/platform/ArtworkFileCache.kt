package org.kasumi321.ushio.phitracker.data.platform

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

interface StandardArtworkCache {
    suspend fun getOrDownloadStandard(songId: String, url: String): String
    fun getStandardIfPresent(songId: String): String?
    fun clearStandard(songIds: Iterable<String>)
    fun clearAllStandard()
}

object NoOpStandardArtworkCache : StandardArtworkCache {
    override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
    override fun getStandardIfPresent(songId: String): String? = null
    override fun clearStandard(songIds: Iterable<String>) = Unit
    override fun clearAllStandard() = Unit
}

class ArtworkFileCache(
    private val httpClient: HttpClient,
    paths: PlatformPaths,
    private val fileSystem: FileSystem = platformFileSystem()
) : StandardArtworkCache {
    private val root: Path = paths.cacheDir.toPath() / "artwork-file-cache"

    override suspend fun getOrDownloadStandard(songId: String, url: String): String {
        val target = standardPath(songId)
        if (fileSystem.exists(target)) return target.toString()

        fileSystem.createDirectories(target.parent ?: root)
        val bytes: ByteArray = httpClient.get(url).body()
        val tmp = target.parent!! / "${target.name}.tmp"
        if (fileSystem.exists(tmp)) {
            fileSystem.delete(tmp)
        }
        fileSystem.write(tmp) {
            write(bytes)
        }
        if (fileSystem.exists(target)) {
            fileSystem.delete(target)
        }
        fileSystem.atomicMove(tmp, target)
        return target.toString()
    }

    override fun getStandardIfPresent(songId: String): String? {
        val path = standardPath(songId)
        return path.toString().takeIf { fileSystem.exists(path) }
    }

    override fun clearStandard(songIds: Iterable<String>) {
        songIds.forEach { songId ->
            val path = standardPath(songId)
            if (fileSystem.exists(path)) {
                fileSystem.delete(path)
            }
        }
    }

    override fun clearAllStandard() {
        val dir = root / "standard"
        if (fileSystem.exists(dir)) {
            fileSystem.deleteRecursively(dir)
        }
    }

    private fun standardPath(songId: String): Path =
        root / "standard" / "${sanitize(songId)}.png"

    private fun sanitize(songId: String): String =
        songId.map { char ->
            if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_'
        }.joinToString("")
}
