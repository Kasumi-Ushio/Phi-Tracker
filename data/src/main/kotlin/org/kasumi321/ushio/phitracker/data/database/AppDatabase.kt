package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecordEntity::class, UserEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun userDao(): UserDao
}
