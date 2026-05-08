package org.kasumi321.ushio.phitracker.data.platform

import no.synth.kmpzip.zip.ZipInputStream

class ZipArchiveReader {
    fun readEntries(data: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(data).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name.substringAfterLast('/')] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }
}

fun createZipArchiveReader(): ZipArchiveReader = ZipArchiveReader()
