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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.ui.home.ProfileHeaderCard
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import org.kasumi321.ushio.phitracker.ui.home.StatsTableCard

@Composable
fun B30ExportLayout(data: B30ExportData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        // 背景层：图片已预模糊，scale(1.2) 防边缘露白
        data.backgroundBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
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
        // 白色蒙版层（65% 不透明度）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = 0.65f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val headerHeight = data.profileCardHeightDp.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileHeaderCard(
                    nickname = data.nickname,
                    displayRks = data.rks,
                    challengeModeRank = data.challengeLevel,
                    moneyString = data.moneyString,
                    avatarBitmap = data.avatarBitmap,
                    onAvatarClick = null,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    textVerticalSpacing = 2.dp,
                    avatarSize = 61.2.dp,
                    avatarTextSpacing = 18.dp,
                    centerContent = true,
                    modifier = Modifier
                        .width(data.profileCardWidthDp.dp)
                        .height(headerHeight)
                )
                StatsTableCard(
                    clearCounts = data.statsTable.clearCounts,
                    fcCount = data.statsTable.fcCount,
                    phiCount = data.statsTable.phiCount,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    rowSpacing = 7.dp,
                    modifier = Modifier
                        .width(data.statsCardWidthDp.dp)
                        .height(headerHeight)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("Phi")
            Spacer(modifier = Modifier.height(6.dp))
            ExportCardGrid(
                cards = data.phiRecords,
                rankLabelProvider = { index -> "P${index + 1}" },
                cardWidth = data.cardWidthDp.dp,
                cardHeight = data.cardHeightDp.dp,
                horizontalGap = data.cardHorizontalGapDp.dp,
                verticalGap = data.cardVerticalGapDp.dp
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Best 27")
            Spacer(modifier = Modifier.height(6.dp))
            ExportCardGrid(
                cards = data.bestRecords,
                rankLabelProvider = { index -> "#${index + 1}" },
                cardWidth = data.cardWidthDp.dp,
                cardHeight = data.cardHeightDp.dp,
                horizontalGap = data.cardHorizontalGapDp.dp,
                verticalGap = data.cardVerticalGapDp.dp
            )

            if (data.overflowRecords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("OVERFLOW")
                Spacer(modifier = Modifier.height(6.dp))
                ExportCardGrid(
                    cards = data.overflowRecords,
                    rankLabelProvider = { index -> "#${index + 1}" },
                    cardWidth = data.cardWidthDp.dp,
                    cardHeight = data.cardHeightDp.dp,
                    horizontalGap = data.cardHorizontalGapDp.dp,
                    verticalGap = data.cardVerticalGapDp.dp
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = data.dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    verticalGap: Dp
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
                    illustrationBitmap = card.illustrationBitmap,
                    illustrationModel = null,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    compactText = true,
                    thumbnailScale = 0.9f,
                    onClick = null,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
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
