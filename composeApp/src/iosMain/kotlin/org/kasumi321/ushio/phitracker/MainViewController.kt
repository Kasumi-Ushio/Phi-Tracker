package org.kasumi321.ushio.phitracker

import androidx.compose.ui.window.ComposeUIViewController
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.logging.createLoggingState
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.di.initKoin
import platform.UIKit.UIViewController

private var iosRuntimeInitialized = false

fun MainViewController(): UIViewController {
    val isDebug = getAppMetadata().buildType == "Debug"
    if (!iosRuntimeInitialized) {
        AppLogger.event("startup", "MainViewController.beforeLogging")
        createLoggingState(isDebug)
        AppLogger.event("startup", "MainViewController.afterLogging")
        initKoin()
        AppLogger.event("startup", "MainViewController.afterKoin")
        iosRuntimeInitialized = true
    }
    return ComposeUIViewController { App() }
}
