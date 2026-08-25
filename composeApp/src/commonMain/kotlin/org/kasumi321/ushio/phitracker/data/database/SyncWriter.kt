package org.kasumi321.ushio.phitracker.data.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import org.kasumi321.ushio.phitracker.data.repository.SyncCommitData
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult

internal class SyncWriter(
    private val database: AppDatabase,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val songSyncHistoryDao: SongSyncHistoryDao
) {
    suspend fun commit(data: SyncCommitData, mode: SyncMode): SyncSaveResult =
        database.useWriterConnection {
            it.immediateTransaction {
                val oldRecords = if (mode == SyncMode.Refresh) recordDao.getAllRecordsOnce() else emptyList()
                val changedEntries = if (mode == SyncMode.Refresh) {
                    data.changedEntriesComparedTo(oldRecords)
                } else {
                    emptyList()
                }

                userDao.insertOrUpdate(data.user)
                recordDao.deleteAll()
                recordDao.insertAll(data.records)

                if (changedEntries.isNotEmpty()) {
                    val snapshotId = syncSnapshotDao.insertAndGetId(data.snapshot)
                    songSyncHistoryDao.insertAll(changedEntries.map { entry -> entry.copy(snapshotId = snapshotId) })
                }

                SyncSaveResult(
                    save = data.save,
                    committedAt = data.committedAt,
                    changedEntryCount = changedEntries.size,
                    snapshotCreated = changedEntries.isNotEmpty()
                )
            }
        }
}
