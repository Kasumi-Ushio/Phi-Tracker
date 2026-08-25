package org.kasumi321.ushio.phitracker.domain.model

enum class SyncMode {
    Bootstrap,
    Refresh
}

data class SyncSaveResult(
    val save: Save,
    val committedAt: Long,
    val changedEntryCount: Int,
    val snapshotCreated: Boolean
)
