package org.kasumi321.ushio.phitracker

import androidx.compose.ui.window.ComposeUIViewController
import org.kasumi321.ushio.phitracker.data.logging.createLoggingState
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.di.initKoin

fun MainViewController() = ComposeUIViewController {
    val isDebug = getAppMetadata().buildType == "Debug"
    createLoggingState(isDebug)
    initKoin()
    App()
}
