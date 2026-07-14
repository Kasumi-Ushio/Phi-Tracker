package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.ui.home.ProfileHeaderCard
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import org.kasumi321.ushio.phitracker.ui.home.StatsTableCard

/**
 * Thumbnail down-scale applied to every export score card. Shared with the B30
 * preloader so it can warm the exact Coil request key the cards consume.
 */
internal const val B30_EXPORT_CARD_THUMBNAIL_SCALE = 0.9f

@Composable
fun B30ExportLayout(data: B30ExportData, allowHardwareImages: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // Pre-blurred bitmap supplied by the platform renderer.
        // Android provides a StackBlur(radius=50) bitmap via B30ImageGenerator.android;
        // iOS also preloads and supplies a blurred bitmap before capturing this
        // shared layout. Production code MUST supply backgroundBitmap — when absent, no image
        // background is rendered (only the white overlay below contributes).
        if (data.backgroundBitmap != null) {
            Image(
                bitmap = data.backgroundBitmap,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    },
                contentScale = ContentScale.Crop,
                alpha = 1f
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background((if (data.darkTheme) Color.Black else Color.White).copy(alpha = 0.65f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val headerHeight = B30ExportSpec.profileCardHeightDp.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileHeaderCard(
                    nickname = data.nickname,
                    displayRks = data.rks,
                    challengeModeRank = data.challengeLevel,
                    moneyString = data.moneyString,
                    avatarUri = data.avatarUri,
                    onAvatarClick = null,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    textVerticalSpacing = 2.dp,
                    avatarSize = 61.2.dp,
                    avatarTextSpacing = 18.dp,
                    centerContent = true,
                    modifier = Modifier
                        .width(B30ExportSpec.profileCardWidthDp.dp)
                        .height(headerHeight),
                    allowHardwareImages = allowHardwareImages
                )
                StatsTableCard(
                    clearCounts = data.statsTable.clearCounts,
                    fcCount = data.statsTable.fcCount,
                    phiCount = data.statsTable.phiCount,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    rowSpacing = 7.dp,
                    modifier = Modifier
                        .width(B30ExportSpec.statsCardWidthDp.dp)
                        .height(headerHeight)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("Phi")
            Spacer(modifier = Modifier.height(6.dp))
            ExportCardGrid(
                cards = data.phiRecords,
                rankLabelProvider = { index -> "P${index + 1}" },
                cardWidth = B30ExportSpec.cardWidthDp.dp,
                cardHeight = B30ExportSpec.cardHeightDp.dp,
                horizontalGap = B30ExportSpec.cardHorizontalGapDp.dp,
                verticalGap = B30ExportSpec.cardVerticalGapDp.dp,
                allowHardwareImages = allowHardwareImages
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Best 27")
            Spacer(modifier = Modifier.height(6.dp))
            ExportCardGrid(
                cards = data.bestRecords,
                rankLabelProvider = { index -> "#${index + 1}" },
                cardWidth = B30ExportSpec.cardWidthDp.dp,
                cardHeight = B30ExportSpec.cardHeightDp.dp,
                horizontalGap = B30ExportSpec.cardHorizontalGapDp.dp,
                verticalGap = B30ExportSpec.cardVerticalGapDp.dp,
                allowHardwareImages = allowHardwareImages
            )

            if (data.overflowRecords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("OVERFLOW")
                Spacer(modifier = Modifier.height(6.dp))
                ExportCardGrid(
                    cards = data.overflowRecords,
                    rankLabelProvider = { index -> "#${index + 1}" },
                    cardWidth = B30ExportSpec.cardWidthDp.dp,
                    cardHeight = B30ExportSpec.cardHeightDp.dp,
                    horizontalGap = B30ExportSpec.cardHorizontalGapDp.dp,
                    verticalGap = B30ExportSpec.cardVerticalGapDp.dp,
                    allowHardwareImages = allowHardwareImages
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Generated by Phi Tracker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = data.dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExportCardGrid(
    cards: List<ExportCardData>,
    rankLabelProvider: (Int) -> String,
    cardWidth: Dp,
    cardHeight: Dp,
    horizontalGap: Dp,
    verticalGap: Dp,
    allowHardwareImages: Boolean = true
) {
    val rows = cards.chunked(3)
    rows.forEachIndexed { rowIndex, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalGap)
        ) {
            row.forEachIndexed { colIndex, card ->
                ScoreCardContent(
                    record = card.record,
                    rank = rowIndex * 3 + colIndex + 1,
                    rankLabel = rankLabelProvider(rowIndex * 3 + colIndex),
                    illustrationUri = card.illustrationUri,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    compactText = true,
                    thumbnailScale = B30_EXPORT_CARD_THUMBNAIL_SCALE,
                    onClick = null,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight),
                    allowHardwareImages = allowHardwareImages
                )
            }
        }
        if (rowIndex < rows.lastIndex) {
            Spacer(modifier = Modifier.height(verticalGap))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
