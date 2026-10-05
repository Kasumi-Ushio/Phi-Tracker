package org.kasumi321.ushio.phitracker.domain.usecase

import kotlin.math.ceil
import kotlin.math.floor
import org.kasumi321.ushio.phitracker.domain.model.SongInfo

/** Inclusive integer bounds covering every chart constant in the song catalog. */
data class SongLevelBounds(
    val min: Int,
    val max: Int
)

/**
 * Derives the chart-constant bounds (filter slider range, target-RKS input
 * caps) from the loaded song catalog, so a future game update (e.g. a Lv.19
 * chart) needs no code change. Returns null when no chart constant exists.
 */
class GetSongLevelBoundsUseCase {
    operator fun invoke(songs: Collection<SongInfo>): SongLevelBounds? =
        fromConstants(songs.flatMap { it.difficulties.values })

    companion object {
        fun fromConstants(constants: Iterable<Float>): SongLevelBounds? {
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            for (constant in constants) {
                if (constant < min) min = constant
                if (constant > max) max = constant
            }
            if (min == Float.MAX_VALUE) return null
            return SongLevelBounds(min = floor(min).toInt(), max = ceil(max).toInt())
        }
    }
}
