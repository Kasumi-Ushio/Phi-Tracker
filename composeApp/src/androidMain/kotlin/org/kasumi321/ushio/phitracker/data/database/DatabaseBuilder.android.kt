package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import org.kasumi321.ushio.phitracker.data.platform.AndroidPlatformContext

actual fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = AndroidPlatformContext.applicationContext
        ?: throw IllegalStateException(
            "AndroidPlatformContext.applicationContext not initialized. " +
                "Call AndroidPlatformContext.initialize(context) in MainActivity.onCreate before App()."
        )
    return Room.databaseBuilder<AppDatabase>(context, "phi_tracker.db")
}
