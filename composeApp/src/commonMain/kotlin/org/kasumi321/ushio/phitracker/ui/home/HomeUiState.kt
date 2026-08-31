package org.kasumi321.ushio.phitracker.ui.home

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState

data class HomeUiState(
    val profile: ProfileUiState = ProfileUiState(),
    val songs: SongsUiState = SongsUiState(),
    val b30: B30UiState = B30UiState(),
    val tools: ToolsUiState = ToolsUiState(),
    val sync: SyncUiState = SyncUiState()
)

data class ProfileUiState(
    val nickname: String = "",
    val challengeModeRank: Int = 0,
    val avatarUri: String? = null,
    val moneyString: String = "",
    val clearCounts: Map<String, Int> = emptyMap(),
    val fcCount: Int = 0,
    val phiCount: Int = 0,
    val lastSyncTime: Long? = null,
    val lastSyncedRecord: BestRecord? = null,
    val recentSyncedRecords: List<BestRecord> = emptyList()
)

data class SongsUiState(
    val searchQuery: String = "",
    val filteredSongs: List<SongInfo> = emptyList(),
    val allSongs: List<SongInfo> = emptyList(),
    val availableChapters: List<String> = emptyList(),
    val selectedChapters: Set<String> = emptySet(),
    val selectedDifficulty: Difficulty? = null,
    val minLevel: Int = 1,
    val maxLevel: Int = 17,
    val showFilterSheet: Boolean = false,
    val illustrationReady: Boolean = true,
    val showPreloadDialog: Boolean = false,
    val preloadProgress: Float = 0f,
    val preloadTotal: Int = 0,
    val preloadCompleted: Int = 0,
    val isPreloading: Boolean = false
)

data class B30UiState(
    val b30: List<BestRecord> = emptyList(),
    val allRecords: List<BestRecord> = emptyList(),
    val displayRks: Float = 0f,
    val themeSettings: PhiTrackerThemeSettings = PhiTrackerThemeSettings(),
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9
)

data class ToolsUiState(
    val syncSnapshots: List<SyncSnapshot> = emptyList(),
    val sessionToken: String? = null,
    val apiEnabled: Boolean = false,
    val useApiData: Boolean = false,
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val apiRksRank: Int? = null,
    val apiTotalUsers: Int? = null,
    val apiHistorySnapshots: List<SyncSnapshot> = emptyList(),
    val apiRankByUser: ApiToolResult = ApiToolResult(),
    val apiRankByPosition: ApiToolResult = ApiToolResult(),
    val apiRksRankResult: ApiToolResult = ApiToolResult(),
    val suggestTargetMode: SuggestTargetMode = SuggestTargetMode.PlayerDisplayRks,
    val suggestTargetInput: String = "",
    val suggestTargetError: String? = null,
    val suggestItems: List<SuggestItem> = emptyList()
)

data class SyncUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle
)
