package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import org.kasumi321.ushio.phitracker.data.platform.createPlatformPaths

actual fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(createPlatformPaths().filesDir + "/phi_tracker.db")
}
