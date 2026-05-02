package org.kasumi321.ushio.phitracker.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import no.synth.kmpzip.io.readBytes
import no.synth.kmpzip.zip.ZipInputStream
import platform.Foundation.NSFileManager
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

actual object ZipExtractor {
    @OptIn(ExperimentalForeignApi::class)
    actual fun extract(zipData: ByteArray, outputDir: String): List<String> {
        NSFileManager.defaultManager.createDirectoryAtPath(
            outputDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        val extractedFiles = mutableListOf<String>()
        val zis = ZipInputStream(zipData)

        try {
            var entry = zis.nextEntry
            while (entry != null) {
                val outputPath = "$outputDir/${entry.name}"

                if (entry.isDirectory) {
                    NSFileManager.defaultManager.createDirectoryAtPath(
                        outputPath,
                        withIntermediateDirectories = true,
                        attributes = null,
                        error = null
                    )
                } else {
                    val parentDir = outputPath.substringBeforeLast("/")
                    if (parentDir.isNotEmpty() && parentDir != outputDir) {
                        NSFileManager.defaultManager.createDirectoryAtPath(
                            parentDir,
                            withIntermediateDirectories = true,
                            attributes = null,
                            error = null
                        )
                    }

                    val entryData = zis.readBytes()
                    entryData.usePinned { pinned ->
                        val file = fopen(outputPath, "wb")
                        if (file != null) {
                            fwrite(pinned.addressOf(0), 1u, entryData.size.toULong(), file)
                            fclose(file)
                        }
                    }
                    zis.closeEntry()
                }

                extractedFiles.add(outputPath)
                entry = zis.nextEntry
            }
        } finally {
            zis.close()
        }

        return extractedFiles
    }
}
