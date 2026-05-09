package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route for the song detail screen (Phase 5).
 *
 * Navigation Compose 2.9.2 uses the [Serializable] annotation to derive
 * the route pattern and argument encoding automatically, handling non-ASCII
 * and reserved characters in [songId] without manual percent-encoding.
 *
 * Usage in [NavHost][androidx.navigation.compose.NavHost]:
 * ```
 * composable<SongDetailRoute> { backStackEntry ->
 *     val route = backStackEntry.toRoute<SongDetailRoute>()
 *     SongDetailScreen(songId = route.songId)
 * }
 * ```
 *
 * Usage to navigate:
 * ```
 * navController.navigate(SongDetailRoute(songId = "光.姜米條.0"))
 * ```
 */
@Serializable
data class SongDetailRoute(val songId: String)
