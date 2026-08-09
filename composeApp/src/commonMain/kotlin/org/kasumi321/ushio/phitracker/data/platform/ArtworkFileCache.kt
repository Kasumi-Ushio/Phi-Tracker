package org.kasumi321.ushio.phitracker.data.platform

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

interface StandardArtworkCache {
    /**
     * Download and retain a low-resolution card thumbnail in app-private
     * persistent storage. Coil's cache is deliberately not used as the source
     * of truth: both Android and iOS may evict their cache directories.
     */
    suspend fun getOrDownloadThumbnail(songId: String, url: String): String
    fun getThumbnailIfPresent(songId: String): String?
    fun hasAllThumbnails(songIds: Iterable<String>): Boolean
    fun clearThumbnails(songIds: Iterable<String>)
    fun clearAllThumbnails()

    suspend fun getOrDownloadStandard(songId: String, url: String): String
    fun getStandardIfPresent(songId: String): String?
    fun clearStandard(songIds: Iterable<String>)
    fun clearAllStandard()
}

object NoOpStandardArtworkCache : StandardArtworkCache {
    override suspend fun getOrDownloadThumbnail(songId: String, url: String): String = url
    override fun getThumbnailIfPresent(songId: String): String? = null
    override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
    override fun clearThumbnails(songIds: Iterable<String>) = Unit
    override fun clearAllThumbnails() = Unit
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
    // These files represent an explicit user-triggered synchronization, not a
    // best-effort image cache. Keep them outside cacheDir so the OS and Coil's
    // LRU do not silently remove a subset between launches.
    private val root: Path = paths.filesDir.toPath() / "artwork-file-cache"

    override suspend fun getOrDownloadThumbnail(songId: String, url: String): String =
        getOrDownload(thumbnailPath(songId), url)

    override fun getThumbnailIfPresent(songId: String): String? = cachedUri(thumbnailPath(songId))

    override fun hasAllThumbnails(songIds: Iterable<String>): Boolean =
        songIds.all { songId -> hasUsableFile(thumbnailPath(songId)) }

    override fun clearThumbnails(songIds: Iterable<String>) {
        songIds.forEach { songId -> deleteIfPresent(thumbnailPath(songId)) }
    }

    override fun clearAllThumbnails() {
        deleteDirectoryIfPresent(root / "thumbnail")
    }

    override suspend fun getOrDownloadStandard(songId: String, url: String): String =
        getOrDownload(standardPath(songId), url)

    private suspend fun getOrDownload(target: Path, url: String): String {
        cachedUri(target)?.let { return it }
        fileSystem.createDirectories(target.parent ?: root)
        val bytes: ByteArray = httpClient.get(url).body()
        require(bytes.isNotEmpty()) { "Downloaded illustration is empty: $url" }
        val tmp = target.parent!! / "${target.name}.tmp"
        try {
            deleteIfPresent(tmp)
            fileSystem.write(tmp) { write(bytes) }
            if (fileSystem.exists(target)) {
                fileSystem.delete(target)
            }
            fileSystem.atomicMove(tmp, target)
        } finally {
            // A failed write must never be mistaken for a completed asset on a
            // subsequent launch.
            deleteIfPresent(tmp)
        }
        return requireNotNull(cachedUri(target)) { "Illustration cache write failed: $target" }
    }

    override fun getStandardIfPresent(songId: String): String? {
        return cachedUri(standardPath(songId))
    }

    override fun clearStandard(songIds: Iterable<String>) {
        songIds.forEach { songId -> deleteIfPresent(standardPath(songId)) }
    }

    override fun clearAllStandard() {
        deleteDirectoryIfPresent(root / "standard")
    }

    private fun cachedUri(path: Path): String? =
        path.toString().takeIf { hasUsableFile(path) }

    private fun hasUsableFile(path: Path): Boolean = runCatching {
        fileSystem.exists(path) && (fileSystem.metadata(path).size ?: 0L) > 0L
    }.getOrDefault(false)

    private fun deleteIfPresent(path: Path) {
        if (fileSystem.exists(path)) fileSystem.delete(path)
    }

    private fun deleteDirectoryIfPresent(path: Path) {
        if (fileSystem.exists(path)) fileSystem.deleteRecursively(path)
    }

    private fun thumbnailPath(songId: String): Path =
        root / "thumbnail" / "${sanitize(songId)}.png"

    private fun standardPath(songId: String): Path =
        root / "standard" / "${sanitize(songId)}.png"

    private fun sanitize(songId: String): String =
        songId.map { char ->
            if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_'
        }.joinToString("")
}
