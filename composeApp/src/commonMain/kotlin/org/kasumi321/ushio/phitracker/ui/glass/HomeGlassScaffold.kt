package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Shared glass frame for the home screen. Owns the single HazeState used by the
 * whole home page: tab content draws full-bleed as the haze source, the top and
 * bottom bars float above it with glass effects. The reported [PaddingValues]
 * describe the bar heights; tab content must use them as scroll padding so the
 * first and last items are never covered by the bars.
 */
@Composable
fun HomeGlassScaffold(
    snackbarHostState: SnackbarHostState,
    topBar: @Composable (HazeState, HazeStyle) -> Unit,
    bottomBar: @Composable (HazeState, HazeStyle) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val hazeState = rememberHazeState()
    val glassStyle = rememberGlassHazeStyle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { topBar(hazeState, glassStyle) },
        bottomBar = { bottomBar(hazeState, glassStyle) }
    ) { innerPadding ->
        // Scaffold lays the bars out as overlays; content stays full-bleed so it
        // can scroll behind them, and uses innerPadding only as scroll padding.
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            content(innerPadding)
        }
    }
}
