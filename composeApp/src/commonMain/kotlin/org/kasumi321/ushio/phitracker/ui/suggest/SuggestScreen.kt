package org.kasumi321.ushio.phitracker.ui.suggest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    val listState = rememberLazyListState()
    // The description block collapses once the list leaves the top and only
    // re-expands when the list is scrolled all the way back up.
    val descriptionVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    // Page-level glass header (own HazeState, like the song detail page): the
    // suggestion list draws full-bleed as the haze source and scrolls under the
    // floating top bar; blur on/off and strength follow the settings entries
    // through LocalGlassSettings.
    val hazeState = rememberHazeState()
    val glassStyle = rememberGlassHazeStyle()

    Scaffold(
            topBar = {
                // Uniform full-strength blur: this bar carries functional
                // controls (chips, input), and a progressive gradient reads
                // as uneven frosting when list cards sit under its bottom edge.
                GlassTopBar(hazeState = hazeState, style = glassStyle, progressiveEndIntensity = 1f) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                                title = { Text("推分建议") },
                                navigationIcon = {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "返回"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            AnimatedVisibility(
                                    visible = descriptionVisible,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Text(
                                            text = "不填则根据当前成绩自动推荐；填写目标 RKS 后按所选模式分析。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text = "玩家最终 RKS 选项将推荐所有有助于将最终 RKS 提升至目标的曲目，单铺面 RKS 选项则仅推荐可达成指定 RKS 的特定单曲。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text = "点击下方的卡片可以查看推分详情。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

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
                    }
                }
            }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            !state.hasSaveData -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    SuggestEmptyHint("请先回到首页同步存档后再使用此功能")
                }
            }
            state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    SuggestEmptyHint(state.targetError ?: "暂无推荐曲目。试试同步数据或调整目标 RKS？")
                }
            }
            else -> {
                LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                        contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = innerPadding.calculateTopPadding() + 12.dp,
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
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
    // Expanded by default: the suggestion details (target ACC, RKS delta) are
    // the primary information on this page, so they should be visible at a
    // glance; the divider arrow still allows collapsing individual cards.
    var expanded by rememberSaveable { mutableStateOf(true) }

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
            marqueeSongTitle = true,
            onClick = { _, _ -> expanded = !expanded },
            footer = {
                val arrowRotation by
                        animateFloatAsState(targetValue = if (expanded) 180f else 0f)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp).rotate(arrowRotation)
                    )
                }
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
