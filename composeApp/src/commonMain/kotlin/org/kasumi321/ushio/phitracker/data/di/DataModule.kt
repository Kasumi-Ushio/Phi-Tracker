package org.kasumi321.ushio.phitracker.data.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.database.AppDatabase
import org.kasumi321.ushio.phitracker.data.database.createAppDatabase
import org.kasumi321.ushio.phitracker.data.parser.AesDecryptor
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.data.platform.createSecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.repository.PhigrosRepositoryImpl
import org.kasumi321.ushio.phitracker.data.repository.SettingsRepositoryImpl
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
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
            install(Logging) { level = LogLevel.HEADERS }
        }
    }
    single { TapTapApiClient(get()) }
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
    single<PhigrosRepository> { PhigrosRepositoryImpl(get(), get(), get(), get(), get()) }
    single { SongDataProvider() }
    single { IllustrationProvider() }
    single { TipsProvider() }
}
