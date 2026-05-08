package org.kasumi321.ushio.phitracker.di

import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.koin.dsl.module

val appModule = module {
    single { SyncSaveUseCase(get()) }
    single { GetB30UseCase(get()) }
    single { GetSuggestUseCase() }
    single { SearchSongUseCase() }
}
