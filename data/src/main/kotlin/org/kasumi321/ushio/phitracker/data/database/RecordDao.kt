package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>)

    @Query("SELECT * FROM records ORDER BY accuracy DESC")
    fun getAllRecords(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records")
    suspend fun getAllRecordsOnce(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE songId = :songId")
    suspend fun getRecordsBySong(songId: String): List<RecordEntity>

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getRecordCount(): Int
}
