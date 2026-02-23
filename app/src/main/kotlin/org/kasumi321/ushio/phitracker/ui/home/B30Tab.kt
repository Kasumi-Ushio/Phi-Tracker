package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.domain.model.BestRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun B30Tab(
    b30: List<BestRecord>,
    displayRks: Float,
    nickname: String,
    challengeModeRank: Int,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onGenerateImage: () -> Unit,
    getIllustrationUrl: (String) -> String?,
    modifier: Modifier = Modifier
) {
    // 分离 Phi3, B27 和 Overflow
    val phi3 = b30.filter { it.isPhi }
    val b36 = b30.filter { !it.isPhi }
    val b27 = b36.take(27)
    val overflow = b36.drop(27)

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Best 30") },
            actions = {
                // 生成图片按钮
                IconButton(
                    onClick = onGenerateImage,
                    enabled = b30.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "生成图片")
                }
                // 刷新按钮
                IconButton(onClick = onRefresh, enabled = !isSyncing) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            }
        )

        // RKS 信息卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rating",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = String.format("%.4f", displayRks),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (b30.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (phi3.isNotEmpty()) {
                            Text(
                                text = "Best φ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = String.format("%.4f", phi3.first().rks),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (b27.size >= 27) {
                            Text(
                                text = "B27 末位: ${String.format("%.4f", b27.last().rks)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (b30.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无数据\n点击右上角刷新同步存档",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Phi3 section
                if (phi3.isNotEmpty()) {
                    item(contentType = "header") {
                        Text(
                            text = "φ Best (AP)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    itemsIndexed(
                        phi3,
                        key = { _, r -> "phi_${r.songId}_${r.difficulty}" },
                        contentType = { _, _ -> "score_card" }
                    ) { index, record ->
                        ScoreCard(
                            rank = index + 1,
                            record = record,
                            illustrationUrl = getIllustrationUrl(record.songId)
                        )
                    }
                }

                // B27 section
                item(contentType = "header") {
                    Text(
                        text = "Best 27",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(
                    b27,
                    key = { _, r -> "b27_${r.songId}_${r.difficulty}" },
                    contentType = { _, _ -> "score_card" }
                ) { index, record ->
                    ScoreCard(
                        rank = index + 1,
                        record = record,
                        illustrationUrl = getIllustrationUrl(record.songId)
                    )
                }

                // Overflow section
                if (overflow.isNotEmpty()) {
                    item(contentType = "header") {
                        Text(
                            text = "Overflow",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(
                        overflow,
                        key = { _, r -> "overflow_${r.songId}_${r.difficulty}" },
                        contentType = { _, _ -> "score_card" }
                    ) { index, record ->
                        ScoreCard(
                            rank = index + 1, // 不跟随 B27 的排名序号
                            record = record,
                            illustrationUrl = getIllustrationUrl(record.songId)
                        )
                    }
                }
            }
        }
    }
}
