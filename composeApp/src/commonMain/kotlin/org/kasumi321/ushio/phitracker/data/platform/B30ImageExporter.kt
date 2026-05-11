package org.kasumi321.ushio.phitracker.data.platform

expect suspend fun saveB30ImageToPictures(pngBytes: ByteArray, fileName: String): Result<Unit>

expect suspend fun shareB30Image(pngBytes: ByteArray, fileName: String): Result<Unit>
