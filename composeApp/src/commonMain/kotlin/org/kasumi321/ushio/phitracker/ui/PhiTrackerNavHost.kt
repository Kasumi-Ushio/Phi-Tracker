package org.kasumi321.ushio.phitracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.toRoute
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageScreen
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationCoordinator
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationGateway
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.home.MainScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginViewModel
import org.kasumi321.ushio.phitracker.ui.navigation.SongDetailRoute
import org.kasumi321.ushio.phitracker.ui.settings.AboutScreen
import org.kasumi321.ushio.phitracker.ui.settings.AcknowledgmentsScreen
import org.kasumi321.ushio.phitracker.ui.settings.DisclaimerScreen
import org.kasumi321.ushio.phitracker.ui.settings.LicensesScreen
import org.kasumi321.ushio.phitracker.ui.settings.PrivacyPolicyScreen
import org.kasumi321.ushio.phitracker.ui.settings.SettingsScreen
import org.kasumi321.ushio.phitracker.ui.settings.SettingsViewModel
import org.kasumi321.ushio.phitracker.ui.song.SongDetailScreen
import org.kasumi321.ushio.phitracker.ui.song.SongDetailViewModel
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
}

private const val NavTransitionDurationMillis = 250

private fun forwardEnterTransition(reducedMotionEnabled: Boolean): EnterTransition =
    if (reducedMotionEnabled) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            initialOffsetX = { fullWidth -> fullWidth / 10 }
        ) + fadeIn(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

private fun forwardExitTransition(reducedMotionEnabled: Boolean): ExitTransition =
    if (reducedMotionEnabled) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            targetOffsetX = { fullWidth -> -fullWidth / 10 }
        ) + fadeOut(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

private fun popEnterTransition(reducedMotionEnabled: Boolean): EnterTransition =
    if (reducedMotionEnabled) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            initialOffsetX = { fullWidth -> -fullWidth / 10 }
        ) + fadeIn(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

private fun popExitTransition(reducedMotionEnabled: Boolean): ExitTransition =
    if (reducedMotionEnabled) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            targetOffsetX = { fullWidth -> fullWidth / 10 }
        ) + fadeOut(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

@Composable
fun PhiTrackerNavHost() {
    AppLogger.event("startup", "NavHost.enter")
    val navController = rememberNavController()
    val b30Navigation = remember(navController) {
        B30NavigationCoordinator(
            object : B30NavigationGateway {
                override fun navigateB30() {
                    navController.navigate(Screen.B30Image.route)
                }

                override fun navigateHomeReplacingLogin() {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }

                override fun navigateLoginReplacingHome() {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }

                override fun popCurrent() {
                    navController.popBackStack()
                }

                override fun popToHome(): Boolean =
                    navController.popBackStack(Screen.Home.route, inclusive = false)

                override fun navigateLoginClearingGraph() {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        )
    }
    val reducedMotionEnabled = rememberReducedMotionEnabled()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        composable(
            route = Screen.Login.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_login") }
            AppLogger.event("startup", "Login.beforeViewModel")
            val loginViewModel: LoginViewModel = koinViewModel()
            AppLogger.event("startup", "Login.afterViewModel")
            LoginScreen(
                onLoginSuccess = b30Navigation::loginSuccess,
                viewModel = loginViewModel
            )
        }
        composable(
            route = Screen.Home.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_home") }
            val homeViewModel: HomeViewModel = koinViewModel()
            MainScreen(
                onLogout = b30Navigation::homeLogout,
                onNavigateToB30Image = b30Navigation::openB30,
                onNavigateToSongDetail = { songId ->
                    navController.navigate(SongDetailRoute.from(songId = songId, difficulty = null))
                },
                onNavigateToSongDetailWithDifficulty = { songId, difficulty ->
                    navController.navigate(SongDetailRoute.from(songId = songId, difficulty = difficulty))
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                viewModel = homeViewModel
            )
        }
        composable(
            route = Screen.B30Image.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) { backStackEntry ->
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_b30image") }
            DisposableEffect(backStackEntry) {
                onDispose {
                    b30Navigation.destinationDisposed()
                }
            }
            val payload = b30Navigation.payload
            if (payload == null) {
                LaunchedEffect(backStackEntry) {
                    b30Navigation.recoverMissingPayload()
                }
                return@composable
            }
            val illustrationResolver: IllustrationUriResolver = koinInject()
            B30ImageScreen(
                b30 = payload.b30,
                displayRks = payload.displayRks,
                nickname = payload.nickname,
                challengeModeRank = payload.challengeModeRank,
                moneyString = payload.moneyString,
                clearCounts = payload.clearCounts,
                fcCount = payload.fcCount,
                phiCount = payload.phiCount,
                avatarUri = payload.avatarUri,
                showB30Overflow = payload.showB30Overflow,
                overflowCount = payload.overflowCount,
                themeSettings = payload.themeSettings,
                tagAnalysis = payload.tagAnalysis,
                getLowIllustrationUrl = illustrationResolver::lowUri,
                getStandardIllustrationUrl = illustrationResolver::standardUri,
                onBack = b30Navigation::toolbarBack
            )
        }
        composable(
            route = Screen.About.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_about") }
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) },
                onNavigateToDisclaimer = { navController.navigate(Screen.Disclaimer.route) },
                onNavigateToAcknowledgments = { navController.navigate(Screen.Acknowledgments.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
            )
        }
        composable(
            route = Screen.Disclaimer.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            DisclaimerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Acknowledgments.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            AcknowledgmentsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Licenses.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LicensesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_settings") }
            val viewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onLogout = b30Navigation::settingsLogout
            )
        }
        composable(
            route = Screen.PrivacyPolicy.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SongDetailRoute>(
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<SongDetailRoute>()
            val songId = route.songId
            val difficulty = route.difficulty()
            val viewModel: SongDetailViewModel = koinViewModel(
                viewModelStoreOwner = backStackEntry,
                parameters = { parametersOf(songId, difficulty ?: org.kasumi321.ushio.phitracker.domain.model.Difficulty.IN) }
            )
            val state by viewModel.uiState.collectAsState()
            LaunchedEffect(songId, difficulty) {
                AppLogger.event(
                    "navigation",
                    "entered_songdetail",
                    mapOf("songId" to songId, "difficulty" to (difficulty?.name ?: "default"))
                )
            }
            val songInfo = state.songInfo
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                songInfo != null -> SongDetailScreen(
                    songInfo = songInfo,
                    userRecords = state.userRecords,
                    syncHistory = state.syncHistory,
                    apiEnabled = state.apiEnabled,
                    useApiData = state.useApiData,
                    apiRequestKey = "${state.apiPlatform.trim()}\u0000${state.apiPlatformId.trim()}\u0000${state.apiUserId.trim()}\u0000${state.displayRks}",
                    getSongApiDetail = viewModel::getSongApiDetail,
                    onLoadSongApiDetail = viewModel::loadSongApiDetail,
                    getChartTags = viewModel::getChartTagState,
                    onLoadChartTags = viewModel::loadChartTags,
                    onSubmitChartTagVote = viewModel::submitChartTagVote,
                    getLowIllustrationUrl = { state.lowIllustrationUrl },
                    getStandardIllustrationUrl = { state.standardIllustrationUrl },
                    initialDifficulty = state.initialDifficulty,
                    onBack = { navController.popBackStack() }
                )
                else -> SongDetailNotFound(songId = songId, onBack = { navController.popBackStack() })
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
                text = "找不到这个曲目",
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
