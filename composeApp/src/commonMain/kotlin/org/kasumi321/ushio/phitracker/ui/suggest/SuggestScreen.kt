package org.kasumi321.ushio.phitracker.ui.suggest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import org.kasumi321.ushio.phitracker.ui.home.formatFour
import org.kasumi321.ushio.phitracker.ui.home.formatTwo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestScreen(
        viewModel: SuggestViewModel,
        getIllustrationUrl: (String) -> String?,
        onNavigateToSongDetail: (String, Difficulty?) -> Unit,
        onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("推分建议") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回"
                                )
                            }
                        }
                )
            }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                        text = "不填则根据当前成绩自动推荐；填写目标 RKS 后按所选模式分析。点击卡片可展开推分详情。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                            selected = state.targetMode == SuggestTargetMode.PlayerDisplayRks,
                            onClick = { viewModel.setTargetMode(SuggestTargetMode.PlayerDisplayRks) },
                            label = { Text("玩家最终 RKS") }
                    )
                    FilterChip(
                            selected = state.targetMode == SuggestTargetMode.SingleChartRks,
                            onClick = { viewModel.setTargetMode(SuggestTargetMode.SingleChartRks) },
                            label = { Text("单谱面 RKS") }
                    )
                }

                OutlinedTextField(
                        value = state.targetInput,
                        onValueChange = { viewModel.setTargetInput(it) },
                        label = { Text("目标 RKS") },
                        placeholder = { Text("例如 16.50") },
                        supportingText = { Text(state.targetError ?: "范围 0.00 到 17.00，最多两位小数") },
                        isError = state.targetError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !state.hasSaveData -> {
                    SuggestEmptyHint("请先回到首页同步存档后再使用此功能")
                }
                state.items.isEmpty() -> {
                    SuggestEmptyHint(state.targetError ?: "暂无推荐曲目。试试同步数据或调整目标 RKS？")
                }
                else -> {
                    LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                                state.items,
                                key = { _, item -> "${item.songId}:${item.difficulty.name}" }
                        ) { index, item ->
                            SuggestScoreCard(
                                    rank = index + 1,
                                    item = item,
                                    illustrationUrl = getIllustrationUrl(item.songId),
                                    onNavigateToSongDetail = onNavigateToSongDetail
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestEmptyHint(message: String) {
    Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuggestScoreCard(
        rank: Int,
        item: SuggestItem,
        illustrationUrl: String?,
        onNavigateToSongDetail: (String, Difficulty?) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val record =
            remember(item) {
                BestRecord(
                        songId = item.songId,
                        songName = item.songName,
                        difficulty = item.difficulty,
                        score = item.currentScore ?: 0,
                        accuracy = item.currentAcc ?: 0f,
                        isFullCombo = item.isFullCombo,
                        chartConstant = item.chartConstant,
                        rks = item.currentRks
                )
            }
    val currentAccText = remember(item.currentAcc) { item.currentAcc?.let { "${it.formatTwo()}%" } ?: "暂无" }
    val targetAccText = remember(item.targetAcc) { "${item.targetAcc.formatTwo()}%" }
    val currentRksText = remember(item.currentRks) { item.currentRks.formatFour() }
    val potentialRksText = remember(item.potentialRks) { item.potentialRks.formatFour() }
    val hasImpact = item.deltaRks > 0.00005f

    ScoreCardContent(
            record = record,
            rank = rank,
            rankLabel = "#$rank",
            illustrationUri = illustrationUrl,
            contentHorizontalPadding = 12.dp,
            contentVerticalPadding = 12.dp,
            compactText = false,
            thumbnailScale = 1f,
            onClick = { _, _ -> expanded = !expanded },
            footer = {
                AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))

                        SuggestDetailRow(label = "目标准确率", value = "$currentAccText → $targetAccText")
                        SuggestDetailRow(label = "单曲 RKS", value = "$currentRksText → $potentialRksText")

                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "总 RKS 变化",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasImpact) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier =
                                                Modifier.clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                            text =
                                                    "${item.newDisplayRks.formatFour()}（+${item.deltaRks.formatFour()}）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                        text = "达成后仍无法进入 B30",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                    onClick = {
                                        onNavigateToSongDetail(item.songId, item.difficulty)
                                    }
                            ) { Text("查看谱面详情") }
                        }
                    }
                }
            }
    )
}

@Composable
private fun SuggestDetailRow(label: String, value: String) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
        )
    }
}
