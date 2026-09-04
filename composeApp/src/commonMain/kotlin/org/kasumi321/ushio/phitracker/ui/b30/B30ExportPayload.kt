package org.kasumi321.ushio.phitracker.ui.b30

import org.kasumi321.ushio.phitracker.domain.model.B30TagAnalysis
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings

class B30ExportPayload(
    b30: List<BestRecord>,
    val displayRks: Float,
    val nickname: String,
    val challengeModeRank: Int,
    val moneyString: String,
    clearCounts: Map<String, Int>,
    val fcCount: Int,
    val phiCount: Int,
    val avatarUri: String?,
    val showB30Overflow: Boolean,
    val overflowCount: Int,
    val themeSettings: PhiTrackerThemeSettings,
    tagAnalysis: B30TagAnalysis? = null
) {
    val b30: List<BestRecord> = b30.toList()
    val clearCounts: Map<String, Int> = clearCounts.toMap()
    val tagAnalysis: B30TagAnalysis? = tagAnalysis
}

internal enum class B30MissingPayloadRecovery {
    HomePopped,
    NavigateLogin
}
