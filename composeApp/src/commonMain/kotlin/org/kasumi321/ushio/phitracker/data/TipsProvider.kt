package org.kasumi321.ushio.phitracker.data

import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createTextAssetReader
import kotlin.random.Random

class TipsProvider(
    private val assetReader: TextAssetReader = createTextAssetReader()
) {
    private val tips: List<String> by lazy {
        runCatching {
            assetReader.readText("tips.txt")
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
        }.getOrDefault(emptyList())
    }

    fun getRandomTip(): String {
        if (tips.isEmpty()) return "Tip: Welcome to PhigrosTracker!"
        val rawTip = tips[Random.nextInt(tips.size)]
        return if (rawTip.startsWith("Tip:", ignoreCase = true)) rawTip else "Tip: $rawTip"
    }
}
