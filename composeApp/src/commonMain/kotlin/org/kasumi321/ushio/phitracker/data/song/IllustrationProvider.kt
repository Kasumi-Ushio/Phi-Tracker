package org.kasumi321.ushio.phitracker.data.song

import org.kasumi321.ushio.phitracker.domain.model.Difficulty

class IllustrationProvider {
    enum class Quality(val path: String) {
        STANDARD("ill"),
        LOW("illLow"),
        BLUR("illBlur")
    }

    private var baseUrl: String = DEFAULT_BASE_URL

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /**
     * Remote illustration URL. Songs in [PER_DIFFICULTY_SONGS] ship one
     * jacket per difficulty upstream (e.g. `ill/IN/{id}.png`, also under
     * illLow/illBlur); they always resolve to a per-difficulty path, using
     * [difficulty] when given and the song's display difficulty (its highest
     * jacketed difficulty) otherwise, so difficulty-less contexts like song
     * cards and B30 exports show the top variant. Every other song keeps the
     * flat `{quality}/{id}.png` layout, so passing a difficulty never changes
     * the URL for them and costs no extra request.
     */
    fun getIllustrationUrl(
        songId: String,
        quality: Quality = Quality.LOW,
        difficulty: Difficulty? = null
    ): String {
        val cleanId = songId.removeSuffix(".0")
        val variant = PER_DIFFICULTY_SONGS[cleanId]?.let { difficulty ?: it }
        val directory = if (variant != null) {
            "${quality.path}/${variant.name}"
        } else {
            quality.path
        }
        return "$baseUrl/$directory/$cleanId.png"
    }

    fun hasPerDifficultyJackets(songId: String): Boolean =
        songId.removeSuffix(".0") in PER_DIFFICULTY_SONGS

    /**
     * Difficulties that ship their own jacket for [songId]: empty for regular
     * songs; for per-difficulty songs every difficulty up to the display
     * (highest) one, matching the full EZ..highest set upstream publishes.
     */
    fun variantDifficulties(songId: String): List<Difficulty> {
        val top = PER_DIFFICULTY_SONGS[songId.removeSuffix(".0")] ?: return emptyList()
        return Difficulty.entries.filter { it.ordinal <= top.ordinal }
    }

    /**
     * Cache slot for a (song, difficulty) pair: songs with per-difficulty
     * jackets get their own slot per difficulty so the variants never
     * overwrite each other, with a null [difficulty] landing on the display
     * (highest) difficulty slot; every other song reuses the flat slot for
     * any difficulty, matching the single flat remote URL.
     */
    fun cacheKey(songId: String, difficulty: Difficulty?): String {
        val variant = PER_DIFFICULTY_SONGS[songId.removeSuffix(".0")]?.let { difficulty ?: it }
        return if (variant != null) "${songId}_${variant.name}" else songId
    }

    fun getStandardUrl(songId: String, difficulty: Difficulty? = null): String =
        getIllustrationUrl(songId, Quality.STANDARD, difficulty)

    fun getLowUrl(songId: String, difficulty: Difficulty? = null): String =
        getIllustrationUrl(songId, Quality.LOW, difficulty)

    fun getBlurUrl(songId: String, difficulty: Difficulty? = null): String =
        getIllustrationUrl(songId, Quality.BLUR, difficulty)

    companion object {
        private const val DEFAULT_BASE_URL =
            "https://gh-proxy.com/https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main"

        /**
         * Clean song ids (without the ".0" suffix) whose upstream repo ships
         * per-difficulty jackets (`ill/{EZ,HD,IN,AT}/{id}.png` and the same
         * under `illLow/` and `illBlur/`), mapped to their highest jacketed
         * difficulty. That highest difficulty is the default display jacket
         * for difficulty-less contexts (song cards, B30 exports), so a song
         * with an AT variant shows AT, one topping out at IN shows IN.
         */
        val PER_DIFFICULTY_SONGS: Map<String, Difficulty> = mapOf(
            "WhatdoyouwantmorethanaHappyending.Apo11oHALOprogramft安月名莉子大瀬良あい" to Difficulty.AT
        )
    }
}
