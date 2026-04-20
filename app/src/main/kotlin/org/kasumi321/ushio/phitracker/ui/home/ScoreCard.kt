package org.kasumi321.ushio.phitracker.ui.home

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors

/**
 * FC/AP/φ 标签颜色
 */
private val FcColor = Color(0xFF4FC3F7)    // 天蓝色
private val ApColor = Color(0xFFFFD54F)    // 金黄色
private val ApTextColor = Color(0xFF5D4037) // AP 标签文字色

/**
 * B30 单条成绩卡片
 *
 * 性能优化:
 * - 所有 String.format 通过 remember 缓存
 * - ImageRequest 使用 remember + size 约束避免重复构建
 * - 使用 @Stable 数据 (BestRecord 是 data class, 天然 stable)
 */
@Composable
fun ScoreCard(
    rank: Int,
    record: BestRecord,
    illustrationUrl: String?,
    onSongClick: (String, Difficulty?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(illustrationUrl) {
        illustrationUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(168) // 56dp * 3 (density)
                .networkCachePolicy(CachePolicy.READ_ONLY)
                .crossfade(200)
                .build()
        }
    }

    ScoreCardContent(
        record = record,
        rank = rank,
        illustrationModel = imageRequest,
        onClick = { onSongClick(record.songId, record.difficulty) },
        modifier = modifier
    )
}

@Composable
fun ScoreCardContent(
    record: BestRecord,
    rank: Int,
    illustrationBitmap: Bitmap? = null,
    illustrationModel: Any? = null,
    rankLabel: String? = null,
    contentHorizontalPadding: Dp = 12.dp,
    contentVerticalPadding: Dp = 12.dp,
    compactText: Boolean = false,
    thumbnailScale: Float = 1f,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val diffColor = DifficultyColors.forDifficulty(record.difficulty)
    val isAp = record.accuracy >= 100f

    // 预计算格式化字符串 (避免每帧重复 String.format)
    val ccText = remember(record.chartConstant, record.difficulty) {
        "${DifficultyColors.labelFor(record.difficulty)} ${String.format("%.1f", record.chartConstant)}"
    }
    val scoreText = remember(record.score) { String.format("%,d", record.score) }
    val accText = remember(record.accuracy) { String.format("%.4f%%", record.accuracy) }
    val rksText = remember(record.rks) { String.format("%.4f", record.rks) }
    val rankText = remember(rank, rankLabel) { rankLabel ?: "#$rank" }
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
    val scaledThumbnailSize = (56f * thumbnailScale.coerceIn(0.5f, 1.5f)).dp

    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
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
                    text = rankText,
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
            when {
                illustrationBitmap != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = illustrationBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(scaledThumbnailSize)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                illustrationModel != null -> {
                    AsyncImage(
                        model = illustrationModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(scaledThumbnailSize)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
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
                    // 难度标签
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

                    // AP/FC 标签
                    when {
                        isAp -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ApColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "φ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApTextColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = tagFontSize
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
                                    fontSize = tagFontSize
                                )
                            }
                        }
                    }
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

@Composable
fun SuggestScoreCard(
    item: SuggestItem,
    illustrationUrl: String?,
    onSongClick: (String, Difficulty?) -> Unit,
    modifier: Modifier = Modifier
) {
    val diffColor = DifficultyColors.forDifficulty(item.difficulty)
    val isAp = (item.currentAcc ?: 0f) >= 100f

    val ccText = remember(item.chartConstant, item.difficulty) {
        "${DifficultyColors.labelFor(item.difficulty)} ${String.format("%.1f", item.chartConstant)}"
    }
    val currentAccText = remember(item.currentAcc) { item.currentAcc?.let { String.format("%.2f%%", it) } ?: "暂无" }
    val targetAccText = remember(item.targetAcc) { String.format("%.2f%%", item.targetAcc) }
    val currentRksText = remember(item.currentRks) { String.format("%.4f", item.currentRks) }
    val potentialRksText = remember(item.potentialRks) { String.format("%.4f", item.potentialRks) }

    val context = LocalContext.current
    val imageRequest = remember(illustrationUrl) {
        illustrationUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(168)
                .networkCachePolicy(CachePolicy.READ_ONLY)
                .crossfade(200)
                .build()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSongClick(item.songId, item.difficulty) },
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
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.songName,
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
                                    text = "φ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApTextColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        item.isFullCombo -> {
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

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currentAccText → $targetAccText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "$currentRksText → $potentialRksText",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
