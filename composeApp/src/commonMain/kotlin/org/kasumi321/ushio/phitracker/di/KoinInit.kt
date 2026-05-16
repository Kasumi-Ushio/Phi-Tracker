package org.kasumi321.ushio.phitracker.di

import org.kasumi321.ushio.phitracker.data.di.dataModule
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.koin.core.context.startKoin

private var koinStarted = false

fun initKoin() {
    if (koinStarted) return
    startKoin {
        modules(dataModule, appModule)
    }
    koinStarted = true
    AppLogger.event("init", "koin", mapOf("status" to "success"))
}

fun initKoinIos() = initKoin()
