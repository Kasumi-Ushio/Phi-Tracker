package org.kasumi321.ushio.phitracker.data.platform

import okio.FileSystem

actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
