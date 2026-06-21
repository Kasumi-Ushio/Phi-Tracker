package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryImplTest {

    private fun createRepo(): SettingsRepositoryImpl {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        return SettingsRepositoryImpl(storage, preloadStorage)
    }

    @Test
    fun defaultsThemeModeZero(): Unit = runTest {
        val repo = createRepo()
        assertEquals(0, repo.themeMode.first())
    }

    @Test
    fun setAndReadThemeMode(): Unit = runTest {
        val repo = createRepo()
        repo.setThemeMode(2)
        assertEquals(2, repo.themeMode.first())
    }

    @Test
    fun defaultsThemeColorSettings(): Unit = runTest {
        val repo = createRepo()
        assertEquals("system", repo.themeColorSource.first())
        assertEquals(-10011977, repo.seedColorArgb.first())
        assertNull(repo.themeImageSeedColorArgb.first())
        assertNull(repo.themeImageUri.first())
        assertEquals("TonalSpot", repo.paletteStyleName.first())
    }

    @Test
    fun setAndReadThemeColorSettings(): Unit = runTest {
        val repo = createRepo()
        repo.setThemeColorSource("seed")
        repo.setSeedColorArgb(-16738680)
        repo.setPaletteStyleName("Vibrant")
        repo.setThemeImageColor("content://image/1", -1499549)

        assertEquals("seed", repo.themeColorSource.first())
        assertEquals(-16738680, repo.seedColorArgb.first())
        assertEquals("Vibrant", repo.paletteStyleName.first())
        assertEquals("content://image/1", repo.themeImageUri.first())
        assertEquals(-1499549, repo.themeImageSeedColorArgb.first())

        repo.clearThemeImageColor()
        assertNull(repo.themeImageUri.first())
        assertNull(repo.themeImageSeedColorArgb.first())
    }

    @Test
    fun defaultsShowB30OverflowFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.showB30Overflow.first())
    }

    @Test
    fun defaultsOverflowCountNine(): Unit = runTest {
        val repo = createRepo()
        assertEquals(9, repo.overflowCount.first())
    }

    @Test
    fun defaultsAvatarUriNull(): Unit = runTest {
        val repo = createRepo()
        assertNull(repo.avatarUri.first())
    }

    @Test
    fun setAndReadAvatarUri(): Unit = runTest {
        val repo = createRepo()
        repo.setAvatarUri("https://example.test/avatar.png")
        assertEquals("https://example.test/avatar.png", repo.avatarUri.first())
    }

    @Test
    fun setAvatarUriNullRemovesKeyAndUpdatesState(): Unit = runTest {
        val repo = createRepo()
        repo.setAvatarUri("https://example.test/avatar.png")
        assertEquals("https://example.test/avatar.png", repo.avatarUri.first())

        repo.setAvatarUri(null)
        assertNull(repo.avatarUri.first())
    }

    @Test
    fun defaultsMoneyStringEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.moneyString.first())
    }

    @Test
    fun setAndReadMoneyString(): Unit = runTest {
        val repo = createRepo()
        repo.setMoneyString("1024.64 KB")
        assertEquals("1024.64 KB", repo.moneyString.first())
    }

    @Test
    fun defaultsIncludePreReleaseFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.includePreRelease.first())
    }

    @Test
    fun setAndReadIncludePreRelease(): Unit = runTest {
        val repo = createRepo()
        repo.setIncludePreRelease(true)
        assertEquals(true, repo.includePreRelease.first())
    }

    @Test
    fun defaultsApiEnabledFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.apiEnabled.first())
    }

    @Test
    fun defaultsUseApiDataFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.useApiData.first())
    }

    @Test
    fun defaultsApiIdEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.apiId.first())
    }

    @Test
    fun setApiIdTrimsWhitespace(): Unit = runTest {
        val repo = createRepo()
        repo.setApiId("  abc123  ")
        assertEquals("abc123", repo.apiId.first())
    }

    @Test
    fun defaultsApiPlatformEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.apiPlatform.first())
    }

    @Test
    fun setApiPlatformTrimsWhitespace(): Unit = runTest {
        val repo = createRepo()
        repo.setApiPlatform("  TapTap  ")
        assertEquals("TapTap", repo.apiPlatform.first())
    }

    @Test
    fun defaultsApiPlatformIdEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.apiPlatformId.first())
    }

    @Test
    fun setApiPlatformIdTrimsWhitespace(): Unit = runTest {
        val repo = createRepo()
        repo.setApiPlatformId("  id_001  ")
        assertEquals("id_001", repo.apiPlatformId.first())
    }

    @Test
    fun storageWritePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage)
        repo1.setThemeMode(3)
        repo1.setMoneyString("999")
        repo1.setApiId("myId")
        repo1.setApiPlatform("platform")
        repo1.setIncludePreRelease(true)
        repo1.setApiEnabled(true)
        repo1.setUseApiData(true)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage)
        assertEquals(3, repo2.themeMode.first())
        assertEquals("999", repo2.moneyString.first())
        assertEquals("myId", repo2.apiId.first())
        assertEquals("platform", repo2.apiPlatform.first())
        assertEquals(true, repo2.includePreRelease.first())
        assertEquals(true, repo2.apiEnabled.first())
        assertEquals(true, repo2.useApiData.first())
    }

    @Test
    fun preloadStorageIsSeparate(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo = SettingsRepositoryImpl(storage, preloadStorage)
        assertEquals(false, repo.getPreloadDone())
        repo.setPreloadDone(true)
        assertEquals(true, repo.getPreloadDone())

        // preloadDone is stored in preloadStorage, not in main storage
        assertEquals(null, storage.map["preload_done"])
        assertEquals("true", preloadStorage.map["preload_done"])
    }

    @Test
    fun defaultsCrashNotificationGuideShownFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.crashNotificationGuideShown.first())
    }

    @Test
    fun setAndReadCrashNotificationGuideShown(): Unit = runTest {
        val repo = createRepo()
        repo.setCrashNotificationGuideShown(true)
        assertEquals(true, repo.crashNotificationGuideShown.first())
    }

    @Test
    fun crashNotificationGuideShownPersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage)
        repo1.setCrashNotificationGuideShown(true)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage)
        assertEquals(true, repo2.crashNotificationGuideShown.first())
    }

    @Test
    fun defaultsAutoCheckUpdateTrue(): Unit = runTest {
        val repo = createRepo()
        assertEquals(true, repo.autoCheckUpdate.first())
    }

    @Test
    fun setAndReadAutoCheckUpdate(): Unit = runTest {
        val repo = createRepo()
        repo.setAutoCheckUpdate(false)
        assertEquals(false, repo.autoCheckUpdate.first())
        repo.setAutoCheckUpdate(true)
        assertEquals(true, repo.autoCheckUpdate.first())
    }

    @Test
    fun autoCheckUpdatePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage)
        repo1.setAutoCheckUpdate(false)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage)
        assertEquals(false, repo2.autoCheckUpdate.first())
    }

    @Test
    fun overflowCountClampedOnReadFromStorage(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("overflow_count", "35")

        val repo = SettingsRepositoryImpl(storage, preloadStorage)
        assertEquals(30, repo.overflowCount.first())
    }

    @Test
    fun overflowCountClampedOnReadFromStorageBelowOne(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("overflow_count", "0")

        val repo = SettingsRepositoryImpl(storage, preloadStorage)
        assertEquals(1, repo.overflowCount.first())
    }

    @Test
    fun setOverflowCountClampsValue(): Unit = runTest {
        val repo = createRepo()
        repo.setOverflowCount(35)
        assertEquals(30, repo.overflowCount.first())
        repo.setOverflowCount(0)
        assertEquals(1, repo.overflowCount.first())
        repo.setOverflowCount(15)
        assertEquals(15, repo.overflowCount.first())
    }
}
