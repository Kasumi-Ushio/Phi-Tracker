package org.kasumi321.ushio.phitracker.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppDatabaseDaoTest {
    private lateinit var database: AppDatabase

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            klass = AppDatabase::class.java
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordDaoStatsUseRealRoomQueries(): Unit = runTest {
        val dao = database.recordDao()
        dao.insertAll(
            listOf(
                RecordEntity("song-a.0", "EZ", 1_000_000, 100f, true, 1L),
                RecordEntity("song-a.0", "HD", 0, 0f, false, 1L),
                RecordEntity("song-b.0", "EZ", 900_000, 95f, false, 1L),
                RecordEntity("song-c.0", "IN", 1_000_000, 100f, true, 1L)
            )
        )

        assertEquals(3, dao.getDistinctSongCount())
        assertEquals(2, dao.getClearCountByDifficulty("EZ"))
        assertEquals(2, dao.getTotalFcCount())
        assertEquals(2, dao.getTotalPhiCount())
    }

    @Test
    fun syncSnapshotDaoUsesRealRoomOrderingAndGeneratedIds(): Unit = runTest {
        val dao = database.syncSnapshotDao()
        val oldId = dao.insertAndGetId(snapshot(timestamp = 1L, nickname = "old"))
        val newId = dao.insertAndGetId(snapshot(timestamp = 2L, nickname = "new"))

        assertEquals(oldId + 1, newId)
        assertEquals("new", dao.getLatest()?.nickname)
        assertEquals(listOf("new", "old"), dao.getAll().first().map { it.nickname })
    }

    @Test
    fun songSyncHistoryDaoUsesRealRoomFiltersAndOrdering(): Unit = runTest {
        val snapshotId = database.syncSnapshotDao().insertAndGetId(snapshot(timestamp = 1L, nickname = "sync"))
        val dao = database.songSyncHistoryDao()
        val entries = listOf(
            history(snapshotId, "song-a.0", "EZ", 900_000, 90f, false, 1L),
            history(snapshotId, "song-a.0", "IN", 1_000_000, 100f, true, 3L),
            history(snapshotId, "song-b.0", "HD", 950_000, 95f, true, 2L)
        )
        dao.insertAll(entries)

        assertEquals(listOf("IN", "EZ"), dao.getBySongId("song-a.0").first().map { it.difficulty })
        assertEquals(listOf("IN"), dao.getRecentBySongId("song-a.0", limit = 1).map { it.difficulty })
        assertEquals(listOf("HD", "IN", "EZ"), dao.getBySnapshotId(snapshotId).map { it.difficulty })
        assertEquals(listOf("IN", "HD"), dao.getRecent(limit = 2).map { it.difficulty })
    }

    @Test
    fun migrationFromV1ToV3PreservesRecordsAndUsers(): Unit = runTest {
        val dbName = "migration_v1_to_v3_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName), null
        )
        rawDb.version = 1
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `records` (
                `songId` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL,
                `isFullCombo` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `users` (
                `playerId` TEXT NOT NULL,
                `nickname` TEXT NOT NULL,
                `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL,
                `background` TEXT NOT NULL,
                `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL,
                `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL,
                `lastSyncAt` INTEGER NOT NULL,
                PRIMARY KEY(`playerId`)
            )
        """.trimIndent())
        rawDb.execSQL("INSERT INTO records VALUES ('song-a.0', 'EZ', 1000000, 100.0, 1, 1)")
        rawDb.execSQL("INSERT INTO records VALUES ('song-b.0', 'HD', 900000, 95.0, 0, 1)")
        rawDb.execSQL("INSERT INTO users VALUES ('player1', 'Alice', '', '', '', 15.0, 1, 1, '', 1)")
        rawDb.close()

        val roomDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        ).setQueryCoroutineContext(Dispatchers.Default)
            .build()

        assertEquals(2, roomDb.recordDao().getRecordCount())
        assertEquals(2, roomDb.recordDao().getDistinctSongCount())
        assertEquals(1, roomDb.recordDao().getClearCountByDifficulty("EZ"))

        val user = roomDb.userDao().getUserOnce()
        assertNotNull(user)
        assertEquals("Alice", user.nickname)

        val snapshotId = roomDb.syncSnapshotDao().insertAndGetId(
            SyncSnapshotEntity(
                timestamp = 1L,
                rks = 15.0f,
                nickname = "migration-test",
                dataCount = 1,
                lastSyncedSongId = "song-a.0",
                lastSyncedDifficulty = "EZ",
                lastSyncedScore = 1000000,
                lastSyncedAccuracy = 100f
            )
        )
        assertNotNull(snapshotId)
        assertEquals(1L, snapshotId)

        val historyDao = roomDb.songSyncHistoryDao()
        historyDao.insertAll(
            listOf(
                SongSyncHistoryEntity(
                    snapshotId = snapshotId,
                    songId = "song-a.0",
                    difficulty = "EZ",
                    score = 1000000,
                    accuracy = 100f,
                    isFullCombo = true,
                    timestamp = 1L
                )
            )
        )
        assertEquals(1, historyDao.getBySnapshotId(snapshotId).size)

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    private fun snapshot(timestamp: Long, nickname: String): SyncSnapshotEntity = SyncSnapshotEntity(
        timestamp = timestamp,
        rks = 15.0f,
        nickname = nickname,
        dataCount = 1,
        lastSyncedSongId = "song-a.0",
        lastSyncedDifficulty = "IN",
        lastSyncedScore = 1_000_000,
        lastSyncedAccuracy = 100f
    )

    private fun history(
        snapshotId: Long,
        songId: String,
        difficulty: String,
        score: Int,
        accuracy: Float,
        isFullCombo: Boolean,
        timestamp: Long
    ): SongSyncHistoryEntity = SongSyncHistoryEntity(
        snapshotId = snapshotId,
        songId = songId,
        difficulty = difficulty,
        score = score,
        accuracy = accuracy,
        isFullCombo = isFullCombo,
        timestamp = timestamp
    )
}
