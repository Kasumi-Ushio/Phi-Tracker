package org.kasumi321.ushio.phitracker.data.song

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem

open class SongDataUpdater(
    private val httpClient: HttpClient,
    private val paths: PlatformPaths,
    private val songDataProvider: SongDataProvider
) {
    companion object {
        private const val BASE_URL =
            "https://gh-proxy.com/https://raw.githubusercontent.com/Catrong/phi-plugin/main/resources/info/"
        val FILE_NAMES = listOf(
            "difficulty.csv",
            "info.csv",
            "infolist.json",
            "notesInfo.json"
        )
    }

    open suspend fun updateAll(
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Result<Unit> {
        val fs = platformFileSystem()
        val songDataDir = paths.filesDir.toPath() / "song_data"
        val cacheDir = paths.cacheDir.toPath()
        val stagingDir = cacheDir / "song_data_staging"
        val backupDir = cacheDir / "song_data_backup"

        fs.createDirectories(songDataDir)
        fs.createDirectories(cacheDir)
        // Clean up stale directories from prior runs
        fs.deleteRecursivelyBestEffort(stagingDir)
        fs.deleteRecursivelyBestEffort(backupDir)
        fs.createDirectories(stagingDir)

        return runCatching {
            // Phase 1: download all files to staging — failure leaves persistent files untouched
            for ((index, fileName) in FILE_NAMES.withIndex()) {
                onProgress(index, FILE_NAMES.size, fileName)
                val url = "$BASE_URL$fileName"
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    throw RuntimeException("Download failed (${response.status}): $url")
                }
                val bytes: ByteArray = response.body()
                val stagedFile = stagingDir / fileName
                val sink = fs.sink(stagedFile).buffer()
                try {
                    sink.write(bytes)
                } finally {
                    sink.close()
                }
            }

            // Phase 2a: back up existing persistent files
            backupExistingFiles(fs, songDataDir, backupDir)

            // Phase 2b: commit staged files to persistent directory
            try {
                for (fileName in FILE_NAMES) {
                    val stagedFile = stagingDir / fileName
                    val targetFile = songDataDir / fileName
                    fs.atomicMove(stagedFile, targetFile)
                }
            } catch (commitError: Exception) {
                // Phase 2c: restore original files from backup
                restoreFromBackup(fs, backupDir, songDataDir)
                throw commitError
            }

            // Invalidate cache only after persistent files are fully updated
            songDataProvider.invalidateCache()
            onProgress(FILE_NAMES.size, FILE_NAMES.size, "完成")
        }.also {
            // Best-effort cleanup — must not hide the primary failure
            fs.deleteRecursivelyBestEffort(stagingDir)
            fs.deleteRecursivelyBestEffort(backupDir)
        }
    }
}

// ── private helpers ────────────────────────────────────────────────

private fun backupExistingFiles(fs: FileSystem, sourceDir: okio.Path, backupDir: okio.Path) {
    fs.createDirectories(backupDir)
    for (fileName in SongDataUpdater.FILE_NAMES) {
        val source = sourceDir / fileName
        if (fs.exists(source)) {
            fs.copy(source, backupDir / fileName)
        }
    }
}

private fun restoreFromBackup(fs: FileSystem, backupDir: okio.Path, targetDir: okio.Path) {
    for (fileName in SongDataUpdater.FILE_NAMES) {
        val backup = backupDir / fileName
        if (fs.exists(backup)) {
            fs.atomicMove(backup, targetDir / fileName)
        }
    }
}

private fun FileSystem.deleteRecursivelyBestEffort(dir: okio.Path) {
    runCatching { if (exists(dir)) deleteRecursively(dir) }
}
