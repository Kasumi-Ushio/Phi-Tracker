package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongSyncHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SongSyncHistoryEntity>)

    @Query("SELECT * FROM song_sync_history WHERE songId = :songId ORDER BY timestamp DESC")
    fun getBySongId(songId: String): Flow<List<SongSyncHistoryEntity>>

    @Query("SELECT * FROM song_sync_history WHERE songId = :songId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBySongId(songId: String, limit: Int = 3): List<SongSyncHistoryEntity>

    @Query("SELECT * FROM song_sync_history WHERE snapshotId = :snapshotId ORDER BY id DESC")
    suspend fun getBySnapshotId(snapshotId: Long): List<SongSyncHistoryEntity>

    @Query("SELECT * FROM song_sync_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 5): List<SongSyncHistoryEntity>
}
