package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SyncSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(snapshot: SyncSnapshotEntity): Long

    @Query("SELECT * FROM sync_snapshots ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SyncSnapshotEntity>>

    @Query("SELECT * FROM sync_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): SyncSnapshotEntity?
}
