package org.kasumi321.ushio.phitracker.data.platform

import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES_KEY = "AppleLanguages"

actual fun currentLanguageTag(): String =
    ((NSUserDefaults.standardUserDefaults.objectForKey(APPLE_LANGUAGES_KEY) as? List<*>)
        ?.firstOrNull() as? String) ?: "en"

actual fun setAppLocale(languageTag: String?): Boolean {
    val defaults = NSUserDefaults.standardUserDefaults
    if (languageTag.isNullOrBlank()) {
        defaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
    } else {
        defaults.setObject(listOf(languageTag), forKey = APPLE_LANGUAGES_KEY)
    }
    // iOS cannot relaunch itself; resource resolution only picks the new
    // language up on the next cold start, so the caller must ask the user
    // to restart manually.
    return true
}
