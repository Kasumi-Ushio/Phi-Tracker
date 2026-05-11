package org.kasumi321.ushio.phitracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageScreen
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.home.MainScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginScreen
import org.kasumi321.ushio.phitracker.ui.navigation.SongDetailRoute
import org.kasumi321.ushio.phitracker.ui.settings.AboutScreen
import org.kasumi321.ushio.phitracker.ui.settings.AcknowledgmentsScreen
import org.kasumi321.ushio.phitracker.ui.settings.DisclaimerScreen
import org.kasumi321.ushio.phitracker.ui.settings.LicensesScreen
import org.kasumi321.ushio.phitracker.ui.song.SongDetailScreen
import org.koin.compose.viewmodel.koinViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object B30Image : Screen("b30image")
    data object About : Screen("about")
    data object Disclaimer : Screen("disclaimer")
    data object Acknowledgments : Screen("acknowledgments")
    data object Licenses : Screen("licenses")
}

/** Simple holder for B30 state passed from Home to B30Image screen. */
private data class B30ImageState(
    val b30: List<BestRecord> = emptyList(),
    val displayRks: Float = 0f,
    val nickname: String = ""
)

@Composable
fun PhiTrackerNavHost() {
    val navController = rememberNavController()
    var b30ImageState by remember { mutableStateOf(B30ImageState()) }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = koinViewModel()
            MainScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToB30Image = { b30, displayRks, nickname ->
                    b30ImageState = B30ImageState(b30, displayRks, nickname)
                    navController.navigate(Screen.B30Image.route)
                },
                onNavigateToSongDetail = { songId ->
                    navController.navigate(SongDetailRoute(songId = songId))
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                },
                viewModel = homeViewModel
            )
        }
        composable(Screen.B30Image.route) {
            B30ImageScreen(
                b30 = b30ImageState.b30,
                displayRks = b30ImageState.displayRks,
                nickname = b30ImageState.nickname,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) },
                onNavigateToDisclaimer = { navController.navigate(Screen.Disclaimer.route) },
                onNavigateToAcknowledgments = { navController.navigate(Screen.Acknowledgments.route) }
            )
        }
        composable(Screen.Disclaimer.route) {
            DisclaimerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Acknowledgments.route) {
            AcknowledgmentsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Licenses.route) {
            LicensesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SongDetailRoute> { backStackEntry ->
            val parentEntry = remember { navController.getBackStackEntry(Screen.Home.route) }
            val homeViewModel: HomeViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
            val state by homeViewModel.uiState.collectAsState()
            val songId = backStackEntry.toRoute<SongDetailRoute>().songId
            val songInfo = state.allSongs.find { it.id == songId }
            if (songInfo != null) {
                val records = state.allRecords.filter { it.songId == songId }
                SongDetailScreen(
                    songInfo = songInfo,
                    userRecords = records,
                    getLowIllustrationUrl = { homeViewModel.getLowIllustrationUrl(it) },
                    getStandardIllustrationUrl = { homeViewModel.getStandardIllustrationUrl(it) },
                    onBack = { navController.popBackStack() }
                )
            } else {
                SongDetailNotFound(songId = songId, onBack = { navController.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongDetailNotFound(songId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("曲目详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "无法找到该曲目",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "曲目 ID: $songId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = onBack,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("返回")
            }
        }
    }
}
