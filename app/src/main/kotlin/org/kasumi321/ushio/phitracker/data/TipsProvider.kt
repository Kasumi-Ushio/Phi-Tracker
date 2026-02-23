package org.kasumi321.ushio.phitracker.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TipsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tips: List<String> by lazy {
        try {
            context.assets.open("tips.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRandomTip(): String {
        if (tips.isEmpty()) return "Tip: Welcome to PhigrosTracker!"
        val rawTip = tips.random()
        return if (rawTip.startsWith("Tip:", ignoreCase = true)) rawTip else "Tip: $rawTip"
    }
}
