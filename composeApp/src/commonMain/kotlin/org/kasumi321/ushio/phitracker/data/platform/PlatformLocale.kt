package org.kasumi321.ushio.phitracker.data.platform

/**
 * Returns the current app locale as a BCP 47 language tag (e.g. `en`, `zh-CN`,
 * `zh-Hant-TW`), used to pick language-variant bundled assets such as tips.
 */
expect fun currentLanguageTag(): String
