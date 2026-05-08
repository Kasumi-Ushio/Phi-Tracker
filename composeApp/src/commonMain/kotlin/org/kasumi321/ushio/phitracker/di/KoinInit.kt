package org.kasumi321.ushio.phitracker.di

import org.kasumi321.ushio.phitracker.data.di.dataModule
import org.koin.core.context.startKoin

private var koinStarted = false

fun initKoin() {
    if (koinStarted) return
    startKoin {
        modules(dataModule, appModule)
    }
    koinStarted = true
}

fun initKoinIos() = initKoin()
