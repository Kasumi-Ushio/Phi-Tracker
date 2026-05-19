package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.Serializable
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/**
 * Type-safe navigation route for the song detail screen (Phase 5).
 *
 * Navigation Compose 2.9.2 uses the [Serializable] annotation to derive
 * the route pattern and argument encoding automatically, handling non-ASCII
 * and reserved characters in [songId] without manual percent-encoding.
 *
 * The optional [difficulty] parameter (Phase C) allows callers such as
 * push-score suggestions to navigate directly to a specific difficulty.
 * When null, the SongDetailScreen defaults to IN as before.
 *
 * Usage in [NavHost][androidx.navigation.compose.NavHost]:
 * ```
 * composable<SongDetailRoute> { backStackEntry ->
 *     val route = backStackEntry.toRoute<SongDetailRoute>()
 *     SongDetailScreen(songId = route.songId, initialDifficulty = route.difficulty)
 * }
 * ```
 *
 * Usage to navigate:
 * ```
 * // Default (string-only, backward compatible)
 * navController.navigate(SongDetailRoute(songId = "光.姜米條.0"))
 *
 * // With difficulty (Phase C suggestion click)
 * navController.navigate(SongDetailRoute(songId = "光.姜米條.0", difficulty = Difficulty.IN))
 * ```
 */
@Serializable
data class SongDetailRoute(val songId: String, val difficulty: Difficulty? = null)
