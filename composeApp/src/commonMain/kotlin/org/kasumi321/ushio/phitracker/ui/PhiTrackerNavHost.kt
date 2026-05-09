package org.kasumi321.ushio.phitracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageScreen
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.home.MainScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginScreen
import org.koin.compose.viewmodel.koinViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object B30Image : Screen("b30image")
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
            val viewModel: HomeViewModel = koinViewModel()
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
                viewModel = viewModel
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
    }
}
