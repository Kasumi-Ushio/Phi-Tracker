package org.kasumi321.ushio.phitracker.ui.settings

import org.kasumi321.ushio.phitracker.data.logging.AppLogger

fun SettingsViewModel.setThemeMode(value: Int) = launchSetting { settingsRepository.setThemeMode(value) }

fun SettingsViewModel.setThemeColorSource(value: String) = launchSetting {
    AppLogger.event("settings", "theme_color_source_changed", mapOf("source" to value))
    settingsRepository.setThemeColorSource(value)
}

fun SettingsViewModel.setSeedColorArgb(value: Int) = launchSetting {
    AppLogger.event("settings", "theme_seed_color_changed")
    settingsRepository.setSeedColorArgb(value)
}

fun SettingsViewModel.setThemeImageColor(uri: String?, seedColorArgb: Int) = launchSetting {
    AppLogger.event("settings", "theme_image_color_selected", mapOf("uriPresent" to (uri != null).toString()))
    settingsRepository.setThemeImageColor(uri, seedColorArgb)
}

fun SettingsViewModel.clearThemeImageColor() = launchSetting {
    AppLogger.event("settings", "theme_image_color_cleared")
    settingsRepository.clearThemeImageColor()
}

fun SettingsViewModel.setPaletteStyleName(value: String) = launchSetting {
    AppLogger.event("settings", "palette_style_changed", mapOf("style" to value))
    settingsRepository.setPaletteStyleName(value)
}

fun SettingsViewModel.setShowB30Overflow(value: Boolean) = launchSetting {
    AppLogger.event("settings", "b30_overflow_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setShowB30Overflow(value)
}

fun SettingsViewModel.setOverflowCount(value: Int) = launchSetting {
    AppLogger.event("settings", "b30_overflow_count_changed", mapOf("count" to value.toString()))
    settingsRepository.setOverflowCount(value)
}

fun SettingsViewModel.setApiEnabled(value: Boolean) = launchSetting {
    AppLogger.event("settings", "api_enabled_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setApiEnabled(value)
}

fun SettingsViewModel.setUseApiData(value: Boolean) = launchSetting {
    AppLogger.event("settings", "use_api_data_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setUseApiData(value)
}

fun SettingsViewModel.setApiUserId(value: String) = launchSetting { settingsRepository.setApiId(value) }
fun SettingsViewModel.setApiPlatform(value: String) = launchSetting { settingsRepository.setApiPlatform(value) }
fun SettingsViewModel.setApiPlatformId(value: String) = launchSetting { settingsRepository.setApiPlatformId(value) }
fun SettingsViewModel.setIncludePreRelease(value: Boolean) = launchSetting { settingsRepository.setIncludePreRelease(value) }
fun SettingsViewModel.setAutoCheckUpdate(value: Boolean) = launchSetting { settingsRepository.setAutoCheckUpdate(value) }
fun SettingsViewModel.setCrashNotificationGuideShown() = launchSetting { settingsRepository.setCrashNotificationGuideShown(true) }
