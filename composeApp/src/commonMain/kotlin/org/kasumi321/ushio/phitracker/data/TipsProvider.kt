package org.kasumi321.ushio.phitracker.data

import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createTextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.currentLanguageTag
import kotlin.random.Random

class TipsProvider(
    private val assetReader: TextAssetReader = createTextAssetReader(),
    private val languageTag: () -> String = { currentLanguageTag() }
) {
    private fun loadTips(): List<String> {
        val tag = languageTag().lowercase()
        val preferred = if (tag.startsWith("zh")) TIPS_ZH else TIPS_EN
        return readTips(preferred).ifEmpty {
            if (preferred == TIPS_ZH) emptyList() else readTips(TIPS_ZH)
        }
    }

    private fun readTips(name: String): List<String> = runCatching {
        assetReader.readText(name)
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
    }.getOrDefault(emptyList())

    fun getRandomTip(): String {
        // Read on every call instead of caching: the in-app language switcher
        // recreates activities without restarting the process, and a cached
        // list would keep showing tips in the previous language.
        val tips = loadTips()
        if (tips.isEmpty()) return "Tip: Welcome to PhigrosTracker!"
        val rawTip = tips[Random.nextInt(tips.size)]
        return if (rawTip.startsWith("Tip:", ignoreCase = true)) rawTip else "Tip: $rawTip"
    }

    private companion object {
        const val TIPS_ZH = "tips.txt"
        const val TIPS_EN = "tips.en.txt"
    }
}
