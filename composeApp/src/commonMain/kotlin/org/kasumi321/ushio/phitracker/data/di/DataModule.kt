package org.kasumi321.ushio.phitracker.data.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.database.AppDatabase
import org.kasumi321.ushio.phitracker.data.database.createAppDatabase
import org.kasumi321.ushio.phitracker.data.parser.AesDecryptor
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.data.platform.createPlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.createSecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.repository.PhigrosRepositoryImpl
import org.kasumi321.ushio.phitracker.data.repository.SettingsRepositoryImpl
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.koin.dsl.module

val dataModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
    single {
        HttpClient {
            install(ContentNegotiation) { json(get()) }
        }
    }
    single { TapTapApiClient(get()) }
    single { PhiPluginApi(get()) }
    single { TapTapQrLoginApi(get()) }
    single { AesDecryptor() }
    single { SaveParser(get()) }
    single<AppDatabase> { createAppDatabase() }
    single { get<AppDatabase>().recordDao() }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().syncSnapshotDao() }
    single { get<AppDatabase>().songSyncHistoryDao() }
    single { TokenManager(createSecureKeyValueStorage("phi_tracker_secure_prefs")) }
    single<SettingsRepository> {
        SettingsRepositoryImpl(
            storage = createSecureKeyValueStorage("phitracker_settings"),
            preloadStorage = createSecureKeyValueStorage("illustration_prefs")
        )
    }
    single { createPlatformPaths() }
    single<PhigrosRepository> { PhigrosRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { SongDataProvider(paths = get()) }
    single { IllustrationProvider() }
    single { TipsProvider() }
    single { SongDataUpdater(get(), get(), get()) }
}
