package org.kasumi321.ushio.phitracker.ui.settings

import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState

data class SettingsUiState(
    val themeMode: Int = 0,
    val themeColorSource: String = "system",
    val seedColorArgb: Int = -10011977,
    val themeImageSeedColorArgb: Int? = null,
    val themeImageUri: String? = null,
    val paletteStyleName: String = "TonalSpot",
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9,
    val isCachingB30Artwork: Boolean = false,
    val b30ArtworkCacheCompleted: Int = 0,
    val b30ArtworkCacheTotal: Int = 0,
    val b30ArtworkCacheError: String? = null,
    val apiEnabled: Boolean = false,
    val useApiData: Boolean = false,
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val isApiTesting: Boolean = false,
    val apiTestMessage: String? = null,
    val isUpdatingData: Boolean = false,
    val updateDataProgress: Int = 0,
    val updateDataTotal: Int = 0,
    val updateDataFileName: String = "",
    val updateDataError: String? = null,
    val includePreRelease: Boolean = false,
    val autoCheckUpdate: Boolean = true,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    val hasRuntimeLogs: Boolean = false,
    val hasCrashLogs: Boolean = false,
    val crashNotificationGuideShown: Boolean = false,
    val tip: String = ""
)

sealed interface SettingsEvent {
    data object LoggedOut : SettingsEvent
    data object RestartRequested : SettingsEvent
}
