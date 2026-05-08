package org.kasumi321.ushio.phitracker

import androidx.compose.ui.window.ComposeUIViewController
import org.kasumi321.ushio.phitracker.di.initKoin

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}
