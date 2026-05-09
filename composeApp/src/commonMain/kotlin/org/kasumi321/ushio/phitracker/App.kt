package org.kasumi321.ushio.phitracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.kasumi321.ushio.phitracker.ui.PhiTrackerNavHost
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme

@Composable
@Preview
fun App() {
    PhiTrackerTheme {
        PhiTrackerNavHost()
    }
}
