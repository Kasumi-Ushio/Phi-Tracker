package org.kasumi321.ushio.phitracker.data.platform

expect suspend fun shareTextLog(text: String, fileName: String): Result<Unit>
