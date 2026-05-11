package org.kasumi321.ushio.phitracker.data.platform

expect suspend fun saveArtworkToPictures(imageUrl: String, fileName: String): Result<Unit>
