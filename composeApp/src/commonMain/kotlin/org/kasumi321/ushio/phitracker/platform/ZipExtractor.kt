package org.kasumi321.ushio.phitracker.platform

expect object ZipExtractor {
    fun extract(zipData: ByteArray, outputDir: String): List<String>
}
