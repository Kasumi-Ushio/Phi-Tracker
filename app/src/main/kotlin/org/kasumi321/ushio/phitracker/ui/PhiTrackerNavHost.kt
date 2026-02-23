package org.kasumi321.ushio.phitracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageScreen
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.home.MainScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object B30Image : Screen("b30image")
}

@Composable
fun PhiTrackerNavHost() {
    val navController = rememberNavController()

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
            MainScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToB30Image = {
                    navController.navigate(Screen.B30Image.route)
                }
            )
        }
        composable(Screen.B30Image.route) {
            // 共享 HomeViewModel (作用域为 Home 的 BackStackEntry)
            val parentEntry = navController.getBackStackEntry(Screen.Home.route)
            val viewModel: HomeViewModel = hiltViewModel(parentEntry)
            val state by viewModel.uiState.collectAsState()

            B30ImageScreen(
                b30 = state.b30,
                displayRks = state.displayRks,
                nickname = state.nickname,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
