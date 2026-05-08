package org.kasumi321.ushio.phitracker.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

expect fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun createAppDatabase(): AppDatabase = createDatabaseBuilder()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
