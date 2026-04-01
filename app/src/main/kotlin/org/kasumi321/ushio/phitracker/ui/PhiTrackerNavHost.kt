package org.kasumi321.ushio.phitracker.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageScreen
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.home.MainScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginScreen
import org.kasumi321.ushio.phitracker.ui.settings.AboutScreen
import org.kasumi321.ushio.phitracker.ui.song.SongDetailScreen
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object B30Image : Screen("b30image")
    data object About : Screen("about")
    data object Disclaimer : Screen("disclaimer")
    data object Acknowledgments : Screen("acknowledgments")
    data object Licenses : Screen("licenses")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Settings : Screen("settings")
    data object SongDetail : Screen("song_detail/{songId}") {
        fun createRoute(songId: String) = "song_detail/$songId"
    }
}

@Composable
fun PhiTrackerNavHost() {
    val navController = rememberNavController()
    val reducedMotionEnabled = rememberReducedMotionEnabled()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        enterTransition = {
            if (reducedMotionEnabled) {
                EnterTransition.None
            } else {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth / 10 },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            }
        },
        exitTransition = {
            if (reducedMotionEnabled) {
                ExitTransition.None
            } else {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 10 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        },
        popEnterTransition = {
            if (reducedMotionEnabled) {
                EnterTransition.None
            } else {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 10 },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            }
        },
        popExitTransition = {
            if (reducedMotionEnabled) {
                ExitTransition.None
            } else {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 10 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        }
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
            MainScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onNavigateToB30Image = {
                    navController.navigate(Screen.B30Image.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                },
                onNavigateToSongDetail = { songId ->
                    navController.navigate(Screen.SongDetail.createRoute(songId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            val parentEntry = remember(navController.currentBackStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrNull()
            }
            if (parentEntry == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
                return@composable
            }
            val viewModel: org.kasumi321.ushio.phitracker.ui.home.HomeViewModel = hiltViewModel(parentEntry)
            org.kasumi321.ushio.phitracker.ui.settings.SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.B30Image.route) {
            // 共享 HomeViewModel (作用域为 Home 的 BackStackEntry)
            val parentEntry = remember(navController.currentBackStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrNull()
            }
            if (parentEntry == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
                return@composable
            }
            val viewModel: HomeViewModel = hiltViewModel(parentEntry)
            val state by viewModel.uiState.collectAsState()

            B30ImageScreen(
                b30 = state.b30,
                displayRks = state.displayRks,
                nickname = state.nickname,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) },
                onNavigateToDisclaimer = { navController.navigate(Screen.Disclaimer.route) },
                onNavigateToAcknowledgments = { navController.navigate(Screen.Acknowledgments.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
            )
        }
        composable(Screen.Acknowledgments.route) {
            org.kasumi321.ushio.phitracker.ui.settings.AcknowledgmentsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Disclaimer.route) {
            org.kasumi321.ushio.phitracker.ui.settings.DisclaimerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Licenses.route) {
            org.kasumi321.ushio.phitracker.ui.settings.LicensesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.PrivacyPolicy.route) {
            org.kasumi321.ushio.phitracker.ui.settings.PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.SongDetail.route,
            arguments = listOf(navArgument("songId") { type = NavType.StringType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable

            val parentEntry = remember(navController.currentBackStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrNull()
            }
            if (parentEntry == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
                return@composable
            }
            val viewModel: HomeViewModel = hiltViewModel(parentEntry)
            val state by viewModel.uiState.collectAsState()

            val songInfo = state.allSongs.find { it.id == songId }
            val userRecords = state.allRecords.filter { it.songId == songId }
            val syncHistory by viewModel.getSyncHistory(songId).collectAsState(initial = emptyList())
            
            if (songInfo != null) {
                SongDetailScreen(
                    songInfo = songInfo,
                    userRecords = userRecords,
                    syncHistory = syncHistory,
                    getIllustrationUrl = { viewModel.getIllustrationUrl(it) },
                    getStandardIllustrationUrl = { viewModel.getStandardIllustrationUrl(it) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
