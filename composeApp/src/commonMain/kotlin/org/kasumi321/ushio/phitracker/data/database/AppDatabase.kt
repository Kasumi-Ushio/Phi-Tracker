package org.kasumi321.ushio.phitracker.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [RecordEntity::class, UserEntity::class, SyncSnapshotEntity::class, SongSyncHistoryEntity::class],
    version = 3,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun userDao(): UserDao
    abstract fun syncSnapshotDao(): SyncSnapshotDao
    abstract fun songSyncHistoryDao(): SongSyncHistoryDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `rks` REAL NOT NULL,
                        `nickname` TEXT NOT NULL,
                        `dataCount` INTEGER NOT NULL,
                        `lastSyncedSongId` TEXT,
                        `lastSyncedDifficulty` TEXT,
                        `lastSyncedScore` INTEGER,
                        `lastSyncedAccuracy` REAL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_sync_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `snapshotId` INTEGER NOT NULL,
                        `songId` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `score` INTEGER NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `isFullCombo` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
