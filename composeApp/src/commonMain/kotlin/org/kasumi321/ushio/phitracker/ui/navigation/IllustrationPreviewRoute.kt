package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.Serializable
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/**
 * Type-safe navigation route for the fullscreen illustration preview screen.
 *
 * Like [SongDetailRoute], the [Serializable] annotation lets Navigation Compose
 * derive the route pattern and argument encoding automatically, so song IDs
 * containing non-ASCII or reserved characters survive the round trip without
 * manual percent-encoding.
 *
 * The payload stays limited to Navigation-supported primitive types: the
 * optional [difficultyName] selects the per-difficulty jacket for the few
 * songs that ship one; when null, the destination resolves the flat
 * high-resolution artwork URI through `IllustrationUriResolver`.
 */
@Serializable
data class IllustrationPreviewRoute(val songId: String, val difficultyName: String? = null) {

    fun difficulty(): Difficulty? =
        difficultyName?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }

    companion object {
        fun from(songId: String, difficulty: Difficulty? = null): IllustrationPreviewRoute =
            IllustrationPreviewRoute(songId = songId, difficultyName = difficulty?.name)
    }
}
