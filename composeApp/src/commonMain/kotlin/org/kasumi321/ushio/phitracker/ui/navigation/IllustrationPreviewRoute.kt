package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route for the fullscreen illustration preview screen.
 *
 * Like [SongDetailRoute], the [Serializable] annotation lets Navigation Compose
 * derive the route pattern and argument encoding automatically, so song IDs
 * containing non-ASCII or reserved characters survive the round trip without
 * manual percent-encoding.
 *
 * The payload stays limited to [songId]: the destination resolves the
 * high-resolution artwork URI itself through `IllustrationUriResolver`.
 */
@Serializable
data class IllustrationPreviewRoute(val songId: String) {

    companion object {
        fun from(songId: String): IllustrationPreviewRoute =
            IllustrationPreviewRoute(songId = songId)
    }
}
