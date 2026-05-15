package org.kasumi321.ushio.phitracker.di

import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.login.LoginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SyncSaveUseCase(get()) }
    single { GetB30UseCase(get()) }
    single { GetSuggestUseCase() }
    single { SearchSongUseCase() }
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel {
        HomeViewModel(
            repository = get(),
            getB30UseCase = get(),
            syncSaveUseCase = get(),
            searchSongUseCase = get(),
            songDataProvider = get(),
            illustrationProvider = get(),
            tipsProvider = get(),
            settingsRepository = get(),
            syncSnapshotDao = get(),
            recordDao = get(),
            songSyncHistoryDao = get(),
            songDataUpdater = get()
        )
    }
}
