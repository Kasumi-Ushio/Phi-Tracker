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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageSpec
import org.kasumi321.ushio.phitracker.ui.b30.setImageRequestAllowHardware
import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import org.kasumi321.ushio.phitracker.ui.components.ScoreRatingTag
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors

internal fun Float.formatScoreCardRks(): String = B30ImageSpec.formatRks(this)

internal fun Float.formatScoreCardLevel(): String = B30ImageSpec.formatChartConstant(this)

private fun Int.formatScore(): String {
    return this.toString().reversed().chunked(3).joinToString(",").reversed()
}

@Composable
fun ScoreCard(
    rank: Int,
    record: BestRecord,
    illustrationUrl: String?,
    onSongClick: (String, Difficulty?) -> Unit,
    modifier: Modifier = Modifier
) {
    ScoreCardContent(
        record = record,
        rank = rank,
        rankLabel = "#$rank",
        illustrationUri = illustrationUrl,
        contentHorizontalPadding = 12.dp,
        contentVerticalPadding = 12.dp,
        compactText = false,
        thumbnailScale = 1f,
        onClick = onSongClick,
        modifier = modifier
    )
}

@Composable
fun ScoreCardContent(
    record: BestRecord,
    rank: Int,
    rankLabel: String,
    illustrationUri: String?,
    contentHorizontalPadding: Dp,
    contentVerticalPadding: Dp,
    compactText: Boolean,
    thumbnailScale: Float,
    onClick: ((String, Difficulty?) -> Unit)?,
    modifier: Modifier = Modifier,
    allowHardwareImages: Boolean = true
) {
    val diffColor = DifficultyColors.forDifficulty(record.difficulty)
    val rating = remember(record.score, record.isFullCombo) {
        ScoreRating.fromScore(record.score, record.isFullCombo)
    }

    val ccText = remember(record.chartConstant, record.difficulty) {
        "${DifficultyColors.labelFor(record.difficulty)} ${record.chartConstant.formatScoreCardLevel()}"
    }
    val scoreText = remember(record.score) { record.score.formatScore() }
    val accText = remember(record.accuracy) { "${record.accuracy.formatScoreCardRks()}%" }
    val rksText = remember(record.rks) { record.rks.formatScoreCardRks() }
    val platformContext = LocalPlatformContext.current
    val scaledThumbnailSize = (56f * thumbnailScale.coerceIn(0.5f, 1.5f)).dp
    val imageRequest = remember(platformContext, illustrationUri) {
        illustrationUri?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(platformContext).apply {
                data(url)
                size((168 * thumbnailScale).toInt())
                networkCachePolicy(CachePolicy.READ_ONLY)
                crossfade(200)
                setImageRequestAllowHardware(allowHardwareImages)
            }.build()
        }
    }

    val rankStyle = if (compactText) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val songStyle = if (compactText) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val scoreStyle = if (compactText) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val accStyle = if (compactText) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val rksStyle = if (compactText) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val tagFontSize = if (compactText) 9.sp else 10.sp
    val rankFontSize = if (compactText) 15.sp else 16.sp
    val songFontSize = if (compactText) 15.sp else 16.sp
    val scoreFontSize = if (compactText) 12.sp else 14.sp
    val accFontSize = if (compactText) 10.sp else 12.sp
    val rksFontSize = if (compactText) 10.sp else 12.sp

    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick(record.songId, record.difficulty) }
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rankLabel,
                    style = rankStyle,
                    fontWeight = FontWeight.Bold,
                    fontSize = rankFontSize,
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.primary
                        in 2..3 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 曲绘缩略图
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(scaledThumbnailSize)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // 曲名 + 难度标签
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = record.songName,
                    style = songStyle,
                    fontWeight = FontWeight.Medium,
                    fontSize = songFontSize,
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
                            fontSize = tagFontSize
                        )
                    }

                    ScoreRatingTag(rating = rating, fontSize = tagFontSize)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧数值
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = if (compactText) {
                    Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                } else {
                    Arrangement.Center
                }
            ) {
                Text(
                    text = scoreText,
                    style = scoreStyle,
                    fontWeight = FontWeight.Bold,
                    fontSize = scoreFontSize,
                    lineHeight = if (compactText) (scoreFontSize.value + 1f).sp else TextUnit.Unspecified
                )
                Text(
                    text = accText,
                    style = accStyle,
                    fontSize = accFontSize,
                    lineHeight = if (compactText) (accFontSize.value + 1f).sp else TextUnit.Unspecified,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = rksText,
                    style = rksStyle,
                    fontWeight = FontWeight.Bold,
                    fontSize = rksFontSize,
                    lineHeight = if (compactText) (rksFontSize.value + 1f).sp else TextUnit.Unspecified,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
