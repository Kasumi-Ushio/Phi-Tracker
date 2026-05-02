package org.kasumi321.ushio.phitracker.platform

expect class FileStorage {
    fun readFile(path: String): ByteArray?
    fun writeFile(path: String, data: ByteArray)
    fun deleteRecursively(path: String)
}
