package org.kasumi321.ushio.phitracker.platform

import net.lingala.zip4j.ZipFile
import java.io.File

actual object ZipExtractor {
    actual fun extract(zipData: ByteArray, outputDir: String): List<String> {
        val tempZip = File.createTempFile("save", ".zip")
        tempZip.writeBytes(zipData)
        val zip = ZipFile(tempZip)
        val outDir = File(outputDir)
        outDir.mkdirs()
        zip.extractAll(outputDir)
        tempZip.delete()
        return outDir.walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .toList()
    }
}
