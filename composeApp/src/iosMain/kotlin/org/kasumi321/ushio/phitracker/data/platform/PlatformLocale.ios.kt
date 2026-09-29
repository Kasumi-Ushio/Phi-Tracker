package org.kasumi321.ushio.phitracker.data.platform

import platform.Foundation.NSUserDefaults

actual fun currentLanguageTag(): String =
    ((NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<*>)
        ?.firstOrNull() as? String) ?: "en"
