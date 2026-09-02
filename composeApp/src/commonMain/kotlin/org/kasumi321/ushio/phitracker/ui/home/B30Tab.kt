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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

@Composable
fun B30Tab(
    state: B30UiState,
    nickname: String,
    challengeModeRank: Int,
    getIllustrationUrl: (String) -> String?,
    onSongClick: (String, Difficulty?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val b30 = state.b30
    val displayRks = state.displayRks
    val showB30Overflow = state.showB30Overflow
    val overflowCount = state.overflowCount
    val phi3 = b30.filter { it.isPhi }
    val b36 = b30.filter { !it.isPhi }
    val b27 = b36.take(27)
    val overflow = if (showB30Overflow) b36.drop(27).take(overflowCount) else emptyList()

    Column(modifier = modifier.fillMaxSize()) {
        // Clears the floating glass top bar; list content scrolls behind it
        Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))

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
                        text = "RKS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = displayRks.formatFour(),
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
                                text = phi3.first().rks.formatFour(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (b27.size >= 27) {
                            Text(
                                text = "B27 末位: ${b27.last().rks.formatFour()}",
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
                    text = "还没有成绩数据\n前往首页同步即可查看",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    // Keeps the last card clear of the floating glass bottom bar
                    bottom = contentPadding.calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            illustrationUrl = getIllustrationUrl(record.songId),
                            onSongClick = onSongClick
                        )
                    }
                }

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
                        illustrationUrl = getIllustrationUrl(record.songId),
                        onSongClick = onSongClick
                    )
                }

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
                            rank = index + 1,
                            record = record,
                            illustrationUrl = getIllustrationUrl(record.songId),
                            onSongClick = onSongClick
                        )
                    }
                }
            }
        }
    }
}
