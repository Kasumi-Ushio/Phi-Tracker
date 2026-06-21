package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

enum class ScoreRating(
    val label: String,
    val color: Color,
    val textColor: Color = Color.White
) {
    False("F", Color(0xFF8E8E93)),
    C("C", Color(0xFF58B4E3)),
    B("B", Color(0xFF2E7D32)),
    A("A", Color(0xFFFF9800)),
    S("S", Color(0xFFFF5DA2)),
    V("V", Color(0xFFD060D8)),
    FullCombo("V", Color(0xFF4FC3F7)),
    Phi("\u03C6", Color(0xFFFFD54F), Color(0xFF5D4037));

    companion object {
        fun fromScore(score: Int, isFullCombo: Boolean): ScoreRating {
            return when {
                score >= 1_000_000 -> Phi
                isFullCombo -> FullCombo
                score >= 960_000 -> V
                score >= 920_000 -> S
                score >= 880_000 -> A
                score >= 820_000 -> B
                score >= 700_000 -> C
                else -> False
            }
        }
    }
}

@Composable
fun ScoreRatingTag(
    rating: ScoreRating,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(rating.color)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = rating.label,
            style = MaterialTheme.typography.labelSmall,
            color = rating.textColor,
            fontWeight = if (rating == ScoreRating.Phi) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = fontSize
        )
    }
}
