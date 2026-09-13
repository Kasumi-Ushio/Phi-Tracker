package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import org.kasumi321.ushio.phitracker.ui.components.phiFontFamily
import org.kasumi321.ushio.phitracker.ui.home.formatScoreCardLevel
import org.kasumi321.ushio.phitracker.ui.home.formatScoreCardRks
import org.kasumi321.ushio.phitracker.ui.home.scoreCardThumbnailSizePx
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors

private fun Int.formatScorePlain(): String {
    // Phigros scores cap at 1,000,000; the game itself renders the 7-digit
    // zero-padded score without any separators.
    return this.toString().padStart(7, '0')
}

/**
 * Reference-style export card (B30ExportCardStyle.Poster), modelled after
 * the Arcaea "Player Bests" card: a full-height illustration on the left edge,
 * the song name in a small top row with the rank pinned to the top-right
 * corner, and an oversized zero-padded score (no separators, matching the
 * in-game score display) as the visual anchor. The info line mirrors
 * the reference's "Potential cc > achieved" phrasing as
 * "chartConstant > single-rks" plus accuracy, and the rating is rendered as
 * colored plain text following the in-game convention (blue V = FC,
 * purple V = 960k+ without FC).
 *
 * The Coil request MUST stay identical to the export preloader's warm-up
 * request (see B30ImageScreen.preloadB30ExportImages): same size key and
 * hardware-bitmap flag, otherwise the memory cache misses and the capture
 * renders blank thumbnails.
 */
@Composable
internal fun ExportPosterCard(
    record: BestRecord,
    rank: Int,
    rankLabel: String,
    illustrationUri: String?,
    modifier: Modifier = Modifier,
    allowHardwareImages: Boolean = true,
    imageSlotId: String? = null,
    onIllustrationSettled: ((slotId: String, error: Throwable?) -> Unit)? = null
) {
    val diffColor = DifficultyColors.forDifficulty(record.difficulty)
    val rating = remember(record.score, record.isFullCombo) {
        ScoreRating.fromScore(record.score, record.isFullCombo)
    }

    val scoreText = remember(record.score) { record.score.formatScorePlain() }
    val ccText = remember(record.chartConstant, record.difficulty) {
        "${DifficultyColors.labelFor(record.difficulty)} ${record.chartConstant.formatScoreCardLevel()}"
    }
    val rksText = remember(record.rks) { record.rks.formatScoreCardRks() }
    val accText = remember(record.accuracy) { "${record.accuracy.formatScoreCardRks()}%" }
    val platformContext = LocalPlatformContext.current
    val imageRequest = remember(platformContext, illustrationUri) {
        illustrationUri?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(platformContext).apply {
                data(url)
                size(scoreCardThumbnailSizePx(B30_EXPORT_CARD_THUMBNAIL_SCALE))
                networkCachePolicy(CachePolicy.READ_ONLY)
                crossfade(200)
                setImageRequestAllowHardware(allowHardwareImages)
            }.build()
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Full-height square illustration on the left edge; the Card's own
            // shape clips its start corners.
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        val slotId = imageSlotId ?: return@AsyncImage
                        when (state) {
                            is AsyncImagePainter.State.Success -> {
                                onIllustrationSettled?.invoke(slotId, null)
                            }
                            is AsyncImagePainter.State.Error -> {
                                onIllustrationSettled?.invoke(slotId, state.result.throwable)
                            }
                            else -> Unit
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Top row: difficulty bar + song name + rank at the far end.
                Row(
                    modifier = Modifier.padding(bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(diffColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicText(
                        text = record.songName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            // bodySmall carries a fixed 16sp line height; left
                            // as-is, an auto-shrunk name drifts off-center
                            // against the 11dp difficulty bar. Unspecified lets
                            // the line height follow the autosized font.
                            lineHeight = TextUnit.Unspecified
                        ),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = 12.sp,
                            stepSize = 1.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rankLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Oversized score: the visual anchor of the whole card.
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                // Info line: chart constant, accuracy+ plain-text single-track ranking score.
                Row(
                    modifier = Modifier.padding(top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ccText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = diffColor
                    )
                    Text(
                        text = " > $rksText · $accText",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = rating.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            // A soft dark halo keeps the bright phi yellow (and
                            // the other rating colors) readable against light
                            // card backgrounds without switching to a badge.
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.25f),
                                offset = Offset.Zero,
                                blurRadius = 6f
                            )
                        ),
                        fontSize = 18.sp,
                        fontFamily = if (rating == ScoreRating.Phi) phiFontFamily else null,
                        fontWeight = if (rating == ScoreRating.Phi) FontWeight.ExtraBold else FontWeight.Bold,
                        color = rating.color
                    )
                }
            }
        }
    }
}
