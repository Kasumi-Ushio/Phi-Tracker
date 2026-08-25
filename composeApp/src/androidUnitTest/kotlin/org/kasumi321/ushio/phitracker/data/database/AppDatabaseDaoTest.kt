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
import kotlin.test.assertFailsWith
import org.kasumi321.ushio.phitracker.data.repository.SyncCommitData
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
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
    fun syncWriterRollsBackAllFourTablesWhenHistoryInsertThrows(): Unit = runTest {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val dbName = "sync_writer_rollback_test.db"
        context.deleteDatabase(dbName)
        var rollbackDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            val recordDao = rollbackDatabase.recordDao()
            val userDao = rollbackDatabase.userDao()
            val snapshotDao = rollbackDatabase.syncSnapshotDao()
            val historyDao = rollbackDatabase.songSyncHistoryDao()
            val originalUser = UserEntity("new-player", "Old", "", "", "", 10f, 1, 1, "CN", 100L)
            val originalRecord = RecordEntity("old-song.0", "EZ", 800_000, 80f, false, 100L)
            userDao.insertOrUpdate(originalUser)
            recordDao.insertAll(listOf(originalRecord))
            val originalSnapshot = snapshot(timestamp = 100L, nickname = "Old")
            val originalSnapshotId = snapshotDao.insertAndGetId(originalSnapshot)
            val originalHistory = history(originalSnapshotId, "old-song.0", "EZ", 800_000, 80f, false, 100L)
            historyDao.insertAll(listOf(originalHistory))
            val beforeUser = userDao.getUserOnce()
            val beforeRecords = recordDao.getAllRecordsOnce()
            val beforeSnapshots = snapshotDao.getAllOnce()
            val beforeHistory = historyDao.getRecent(limit = 100)
            val throwingHistoryDao = object : SongSyncHistoryDao by historyDao {
                override suspend fun insertAll(entries: List<SongSyncHistoryEntity>) {
                    historyDao.insertAll(entries)
                    error("injected after real history insert")
                }
            }
            val writer = SyncWriter(rollbackDatabase, recordDao, userDao, snapshotDao, throwingHistoryDao)
            val commitData = SyncCommitData(
                save = emptySave(),
                committedAt = 200L,
                user = UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L),
                records = listOf(RecordEntity("new-song.0", "IN", 950_000, 95f, true, 200L)),
                snapshot = snapshot(timestamp = 200L, nickname = "New")
            )

            assertFailsWith<IllegalStateException> {
                writer.commit(commitData, SyncMode.Refresh)
            }

            rollbackDatabase.close()
            rollbackDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()

            assertEquals(beforeUser, rollbackDatabase.userDao().getUserOnce())
            assertEquals(beforeRecords, rollbackDatabase.recordDao().getAllRecordsOnce())
            assertEquals(beforeSnapshots, rollbackDatabase.syncSnapshotDao().getAllOnce())
            assertEquals(beforeHistory, rollbackDatabase.songSyncHistoryDao().getRecent(limit = 100))
        } finally {
            rollbackDatabase.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun syncWriterBootstrapReplacesPlayerDataWithoutSnapshotOrHistory(): Unit = runTest {
        val originalSnapshotId = database.syncSnapshotDao().insertAndGetId(snapshot(100L, "Old"))
        database.songSyncHistoryDao().insertAll(
            listOf(history(originalSnapshotId, "old-song.0", "EZ", 800_000, 80f, false, 100L))
        )
        val beforeSnapshots = database.syncSnapshotDao().getAllOnce()
        val beforeHistory = database.songSyncHistoryDao().getRecent(100)
        val writer = writer()

        val result = writer.commit(commitData(), SyncMode.Bootstrap)

        assertEquals(UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L), database.userDao().getUserOnce())
        assertEquals(commitData().records, database.recordDao().getAllRecordsOnce())
        assertEquals(beforeSnapshots, database.syncSnapshotDao().getAllOnce())
        assertEquals(beforeHistory, database.songSyncHistoryDao().getRecent(100))
        assertEquals(0, result.changedEntryCount)
        assertEquals(false, result.snapshotCreated)
    }

    @Test
    fun syncWriterUnchangedRefreshDoesNotCreateSnapshotOrHistory(): Unit = runTest {
        database.recordDao().insertAll(
            listOf(RecordEntity("new-song.0", "IN", 950_000, 95f, false, 100L))
        )
        val data = commitData().copy(
            records = listOf(RecordEntity("new-song.0", "IN", 950_000, 95f, true, 200L))
        )

        val result = writer().commit(data, SyncMode.Refresh)

        assertEquals(0, result.changedEntryCount)
        assertEquals(false, result.snapshotCreated)
        assertEquals(true, database.recordDao().getAllRecordsOnce().single().isFullCombo)
        assertEquals(emptyList(), database.syncSnapshotDao().getAllOnce())
        assertEquals(emptyList(), database.songSyncHistoryDao().getRecent(100))
    }

    @Test
    fun syncWriterChangedRefreshCreatesOneSnapshotAndAssociatedHistory(): Unit = runTest {
        database.recordDao().insertAll(
            listOf(RecordEntity("new-song.0", "IN", 900_000, 90f, false, 100L))
        )

        val result = writer().commit(commitData(), SyncMode.Refresh)

        val snapshot = database.syncSnapshotDao().getAllOnce().single()
        val history = database.songSyncHistoryDao().getBySnapshotId(snapshot.id).single()
        assertEquals(1, result.changedEntryCount)
        assertEquals(true, result.snapshotCreated)
        assertEquals("new-song.0", history.songId)
        assertEquals(950_000, history.score)
        assertEquals(200L, history.timestamp)
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

    @Test
    fun migrationFromV2ToV3PreservesRecordsUsersAndSnapshots(): Unit = runTest {
        val dbName = "migration_v2_to_v3_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        val rawDb = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        rawDb.version = 2
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `records` (
                `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `users` (
                `playerId` TEXT NOT NULL, `nickname` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL, `background` TEXT NOT NULL, `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL, `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`playerId`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL,
                `rks` REAL NOT NULL, `nickname` TEXT NOT NULL, `dataCount` INTEGER NOT NULL,
                `lastSyncedSongId` TEXT, `lastSyncedDifficulty` TEXT, `lastSyncedScore` INTEGER,
                `lastSyncedAccuracy` REAL
            )
        """.trimIndent())
        rawDb.execSQL("INSERT INTO records VALUES ('song-v2.0', 'HD', 900000, 95.0, 0, 2)")
        rawDb.execSQL("INSERT INTO users VALUES ('player-v2', 'V2', '', '', '', 15.0, 1, 2, 'CN', 2)")
        rawDb.execSQL("INSERT INTO sync_snapshots VALUES (7, 2, 15.0, 'V2', 1, 'song-v2.0', 'HD', 900000, 95.0)")
        rawDb.close()

        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

        assertEquals("song-v2.0", roomDb.recordDao().getAllRecordsOnce().single().songId)
        assertEquals("V2", roomDb.userDao().getUserOnce()?.nickname)
        assertEquals(7L, roomDb.syncSnapshotDao().getAllOnce().single().id)
        roomDb.songSyncHistoryDao().insertAll(
            listOf(history(7L, "song-v2.0", "HD", 900_000, 95f, false, 2L))
        )
        assertEquals(1, roomDb.songSyncHistoryDao().getBySnapshotId(7L).size)

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

    private fun emptySave() = Save(
        gameRecord = emptyMap(),
        gameProgress = GameProgress(
            false, false, false, false, "", 0, 0, emptyList(),
            0, 0, 0, 0, null, null, null, null, null
        ),
        user = UserSettings(false, "", "", ""),
        summary = null
    )

    private fun writer() = SyncWriter(
        database,
        database.recordDao(),
        database.userDao(),
        database.syncSnapshotDao(),
        database.songSyncHistoryDao()
    )

    private fun commitData() = SyncCommitData(
        save = emptySave(),
        committedAt = 200L,
        user = UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L),
        records = listOf(RecordEntity("new-song.0", "IN", 950_000, 95f, true, 200L)),
        snapshot = snapshot(200L, "New")
    )
}
