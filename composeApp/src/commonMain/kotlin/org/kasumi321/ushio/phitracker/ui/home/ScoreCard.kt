package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors

private val FcColor = Color(0xFF4FC3F7)
private val ApColor = Color(0xFFFFD54F)
private val ApTextColor = Color(0xFF5D4037)

private fun Float.formatRks(): String {
    val v = (this * 10000).toLong()
    return "${v / 10000}.${(kotlin.math.abs(v) % 10000).toString().padStart(4, '0')}"
}

private fun Float.formatLevel(): String {
    val v = (this * 10).toLong()
    return "${v / 10}.${kotlin.math.abs(v % 10)}"
}

private fun Int.formatScore(): String {
    return this.toString().reversed().chunked(3).joinToString(",").reversed()
}

@Composable
fun ScoreCard(
    rank: Int,
    record: BestRecord,
    illustrationUrl: String?,
    onSongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val diffColor = DifficultyColors.forDifficulty(record.difficulty)
    val isAp = record.accuracy >= 100f

    val ccText = remember(record.chartConstant, record.difficulty) {
        "${DifficultyColors.labelFor(record.difficulty)} ${record.chartConstant.formatLevel()}"
    }
    val scoreText = remember(record.score) { record.score.formatScore() }
    val accText = remember(record.accuracy) { "${record.accuracy.formatRks()}%" }
    val rksText = remember(record.rks) { record.rks.formatRks() }
    val platformContext = LocalPlatformContext.current
    val imageRequest = remember(platformContext, illustrationUrl) {
        illustrationUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(platformContext)
                .data(url)
                .size(168)
                .networkCachePolicy(CachePolicy.READ_ONLY)
                .crossfade(200)
                .build()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth()
            .clickable { onSongClick(record.songId) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.primary
                        in 2..3 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = record.songName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(diffColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ccText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    when {
                        isAp -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ApColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "\u03C6",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApTextColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        record.isFullCombo -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(FcColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "FC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = accText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = rksText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
