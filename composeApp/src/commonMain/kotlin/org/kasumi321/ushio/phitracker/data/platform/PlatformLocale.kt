package org.kasumi321.ushio.phitracker.data.platform

/**
 * Returns the current app locale as a BCP 47 language tag (e.g. `en`, `zh-CN`,
 * `zh-Hant-TW`), used to pick language-variant bundled assets such as tips.
 */
expect fun currentLanguageTag(): String

/**
 * Applies [languageTag] (BCP 47, e.g. `en`, `zh-Hans`, `zh-Hant`) as the
 * per-app locale; null or blank restores following the system language.
 * Returns true when the change only takes effect after a manual app restart
 * (iOS), false when it is applied immediately (Android).
 */
expect fun setAppLocale(languageTag: String?): Boolean
