package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataThresholding
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.platform.copyToClipboard
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import org.kasumi321.ushio.phitracker.ui.components.ScoreRatingTag
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ToolsTab(
    syncSnapshots: List<SyncSnapshotEntity>,
    sessionToken: String?,
    apiEnabled: Boolean,
    useApiData: Boolean,
    defaultRks: Float,
    apiRankByUser: ApiToolResult,
    apiRankByPosition: ApiToolResult,
    apiRksRankResult: ApiToolResult,
    suggestTargetMode: SuggestTargetMode,
    suggestTargetInput: String,
    suggestTargetError: String?,
    suggestItems: List<SuggestItem>,
    onSuggestTargetModeChange: (SuggestTargetMode) -> Unit,
    onSuggestTargetInputChange: (String) -> Unit,
    onFetchRankByUser: () -> Unit,
    onFetchRankByPosition: (Int) -> Unit,
    onFetchRksRank: (Float) -> Unit,
    onSuggestionClick: (String, Difficulty?) -> Unit,
    getIllustrationUrl: (String) -> String?,
    tip: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("工具")
                        if (tip.isNotBlank()) {
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(0.75f).basicMarquee()
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            CollapsibleToolCard(
                title = "等效 RKS 计算器",
                subtitle = "根据定数和准确率计算等效 RKS",
                icon = Icons.Default.Calculate
            ) { RksCalculatorContent() }

            CollapsibleToolCard(
                title = "推分建议",
                subtitle = "根据当前 B30 和存档数据推荐可推分曲目",
                icon = Icons.Default.ShowChart
            ) {
                SuggestionContent(
                    targetMode = suggestTargetMode,
                    targetInput = suggestTargetInput,
                    targetError = suggestTargetError,
                    suggestItems = suggestItems,
                    onTargetModeChange = onSuggestTargetModeChange,
                    onTargetInputChange = onSuggestTargetInputChange,
                    onSuggestionClick = onSuggestionClick,
                    getIllustrationUrl = getIllustrationUrl
                )
            }

            CollapsibleToolCard(
                title = "RKS 历史变化",
                subtitle = "查看每次同步后的 RKS 趋势",
                icon = Icons.Default.ShowChart
            ) { RksHistoryChartContent(syncSnapshots) }

            if (apiEnabled && useApiData) {
                CollapsibleToolCard(
                    title = "排行榜（按用户）",
                    subtitle = "查询当前玩家的排名情况",
                    icon = Icons.Default.AccountCircle
                ) { ApiRankByUserContent(state = apiRankByUser, onFetch = onFetchRankByUser) }

                CollapsibleToolCard(
                    title = "排行榜（按名次）",
                    subtitle = "输入名次查询对应玩家信息",
                    icon = Icons.Default.DataThresholding
                ) {
                    ApiRankByPositionContent(
                        state = apiRankByPosition,
                        onFetch = onFetchRankByPosition
                    )
                }

                CollapsibleToolCard(
                    title = "RKS 区间统计",
                    subtitle = "查询大于给定 RKS 的用户数量",
                    icon = Icons.Default.AreaChart
                ) {
                    ApiRksRankContent(
                        state = apiRksRankResult,
                        defaultRks = defaultRks,
                        onFetch = onFetchRksRank
                    )
                }
            }

            CollapsibleToolCard(
                title = "sessionToken 导出",
                subtitle = "查看并复制当前登录凭证",
                icon = Icons.Default.ContentCopy
            ) { SessionTokenContent(sessionToken) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CollapsibleToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RksCalculatorContent() {
    var chartConstantInput by rememberSaveable { mutableStateOf("") }
    var accuracyInput by rememberSaveable { mutableStateOf("") }

    val chartConstant = chartConstantInput.toFloatOrNull()
    val accuracy = accuracyInput.toFloatOrNull()

    val resultRks = if (chartConstant != null && accuracy != null && accuracy in 0f..100f && chartConstant >= 0f) {
        RksCalculator.calculateSingleRks(accuracy, chartConstant)
    } else null

    Text(
        text = "计算公式：RKS = ((acc − 55) / 45)² × 定数",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = chartConstantInput,
            onValueChange = { chartConstantInput = it },
            label = { Text("谱面定数") },
            placeholder = { Text("15.3") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = accuracyInput,
            onValueChange = { accuracyInput = it },
            label = { Text("准确率") },
            placeholder = { Text("97.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "计算结果",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (resultRks != null) resultRks.formatFour() else "\u2014",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (resultRks != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RksHistoryChartContent(snapshots: List<SyncSnapshotEntity>) {
    if (snapshots.size < 2) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (snapshots.isEmpty()) "暂无同步记录\n同步存档后，数据将在此处展示" else "需要至少 2 次同步记录才能绘制趋势图",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val sorted = remember(snapshots) { snapshots.sortedBy { it.timestamp } }
    val minRks = remember(sorted) { sorted.minOf { it.rks } }
    val maxRks = remember(sorted) { sorted.maxOf { it.rks } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "范围: ${minRks.formatFour()} ~ ${maxRks.formatFour()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "共 ${snapshots.size} 次同步",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // RKS History Line Chart (180dp, CMP-safe)
        val rksValues = sorted.map { it.rks }
        val chartMin = rksValues.min()
        val chartMax = rksValues.max()
        val range = chartMax - chartMin
        val padding = if (range > 0f) range * 0.1f else 0.5f
        val yMin = chartMin - padding
        val yMax = chartMax + padding
        val yRange = yMax - yMin
        val gridLines = 5
        val labelColumnWidth = 40.dp
        val chartPadding = 8.dp
        val circleRadius = 4.dp
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        val lineColor = MaterialTheme.colorScheme.primary
        val pointColor = MaterialTheme.colorScheme.primary

        Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(labelColumnWidth)
                    .fillMaxHeight()
                    .padding(top = chartPadding, bottom = chartPadding),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(gridLines) { i ->
                    val value = yMax - (yRange * i / (gridLines - 1))
                    Text(
                        text = value.formatTwo(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Canvas chart area
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val width = size.width
                val height = size.height
                val chartHeight = height - chartPadding.toPx() * 2

                // Grid lines
                repeat(gridLines) { i ->
                    val y = chartPadding.toPx() + (chartHeight * i / (gridLines - 1))
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Map data points to canvas coordinates
                val points = rksValues.mapIndexed { index, rks ->
                    val x = if (rksValues.size == 1) width / 2f else {
                        chartPadding.toPx() + (index.toFloat() / (rksValues.size - 1)) * (width - chartPadding.toPx() * 2)
                    }
                    val y = chartPadding.toPx() + ((yMax - rks) / yRange) * chartHeight
                    Offset(x, y)
                }

                // Line path
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }

                // Data points
                points.forEach { offset ->
                    drawCircle(
                        color = pointColor,
                        radius = circleRadius.toPx(),
                        center = offset
                    )
                }
            }
        }

        HorizontalDivider()

        val recent = sorted.sortedByDescending { it.timestamp }.take(10)
        recent.forEachIndexed { index, snapshot ->
            val prevRks = if (index + 1 < recent.size) recent[index + 1].rks else null
            val delta = if (prevRks != null) snapshot.rks - prevRks else null

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = epochMillisToShortDateString(snapshot.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (delta != null && delta != 0f) {
                        Text(
                            text = if (delta > 0) "+${delta.formatFour()}" else delta.formatFour(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (delta > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(72.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = snapshot.rks.formatFour(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(76.dp)
                    )
                }
            }
            if (index < recent.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun ApiRankByUserContent(state: ApiToolResult, onFetch: () -> Unit) {
    OutlinedButton(
        onClick = onFetch,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("查询当前用户排名")
    }
    ApiToolResultPanel(state = state)
}

@Composable
private fun ApiRankByPositionContent(state: ApiToolResult, onFetch: (Int) -> Unit) {
    var rankInput by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = rankInput,
            onValueChange = { rankInput = it },
            label = { Text("名次") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onFetch(rankInput.toIntOrNull() ?: -1) }, enabled = !state.isLoading) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("查询")
            }
        }
    }
    ApiToolResultPanel(state = state)
}

@Composable
private fun ApiRksRankContent(state: ApiToolResult, defaultRks: Float, onFetch: (Float) -> Unit) {
    var rksInput by rememberSaveable(defaultRks) {
        mutableStateOf(if (defaultRks > 0f) defaultRks.formatFour() else "")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = rksInput,
            onValueChange = { rksInput = it },
            label = { Text("目标 RKS") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onFetch(rksInput.toFloatOrNull() ?: -1f) }, enabled = !state.isLoading) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("查询")
            }
        }
    }
    ApiToolResultPanel(state = state)
}

@Composable
private fun ApiToolResultPanel(state: ApiToolResult) {
    if (state.rows.isNotEmpty()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.rows.forEach { row ->
                    RankInfoRow(label = row.label, value = row.value)
                }
            }
        }
        return
    }

    Text(
        text = state.message ?: "尚未查询",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RankInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f)
        )
    }
}

@Composable
private fun SessionTokenContent(sessionToken: String?) {
    var showTokenDialog by remember { mutableStateOf(false) }

    if (sessionToken == null) {
        Text(
            text = "当前未登录，无法导出。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        OutlinedButton(onClick = { showTokenDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("显示 sessionToken")
        }
    }

    if (showTokenDialog && sessionToken != null) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("安全提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "sessionToken 是您的账号凭证，拥有此 Token 的人可以读取您的游戏存档。\n\n请勿将此 Token 分享给任何不信任的人。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = sessionToken,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard("sessionToken", sessionToken)
                        showPlatformMessage("已复制到剪贴板")
                        showTokenDialog = false
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制并关闭")
                }
            },
            dismissButton = { TextButton(onClick = { showTokenDialog = false }) { Text("关闭") } }
        )
    }
}

// ══════════════════════════════════════════════════════════════
// 推分建议
// ══════════════════════════════════════════════════════════════

@Composable
private fun SuggestionContent(
    targetMode: SuggestTargetMode,
    targetInput: String,
    targetError: String?,
    suggestItems: List<SuggestItem>,
    onTargetModeChange: (SuggestTargetMode) -> Unit,
    onTargetInputChange: (String) -> Unit,
    onSuggestionClick: (String, Difficulty?) -> Unit,
    getIllustrationUrl: (String) -> String?
) {
    Text(
        text = "留空时沿用当前 B30 阈值；输入目标后按所选模式重新推荐。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = targetMode == SuggestTargetMode.PlayerDisplayRks,
            onClick = { onTargetModeChange(SuggestTargetMode.PlayerDisplayRks) },
            label = { Text("玩家最终 RKS") }
        )
        FilterChip(
            selected = targetMode == SuggestTargetMode.SingleChartRks,
            onClick = { onTargetModeChange(SuggestTargetMode.SingleChartRks) },
            label = { Text("单谱面 RKS") }
        )
    }

    OutlinedTextField(
        value = targetInput,
        onValueChange = onTargetInputChange,
        label = { Text("目标 RKS") },
        placeholder = { Text("例如 16.50") },
        supportingText = {
            Text(targetError ?: "范围 0.00 到 17.00，最多两位小数")
        },
        isError = targetError != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )

    if (suggestItems.isEmpty()) {
        Text(
            text = targetError ?: "暂无推分建议（请先同步存档，或调整目标 RKS）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val pageSize = 5
    val cappedItems = remember(suggestItems) { suggestItems.take(30) }
    val totalPages = remember(cappedItems) { ceil(cappedItems.size / pageSize.toFloat()).toInt().coerceAtLeast(1) }
    var currentPage by rememberSaveable(cappedItems.size) { mutableStateOf(0) }
    currentPage = currentPage.coerceIn(0, totalPages - 1)

    val start = currentPage * pageSize
    val end = (start + pageSize).coerceAtMost(cappedItems.size)
    val pageItems = cappedItems.subList(start, end)

    pageItems.forEach { item ->
        SuggestScoreCard(
            item = item,
            illustrationUrl = getIllustrationUrl(item.songId),
            onSuggestionClick = onSuggestionClick
        )
    }

    if (totalPages > 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                enabled = currentPage > 0
            ) {
                Text("上一页")
            }
            Text(
                text = "第 ${currentPage + 1} / $totalPages 页",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                enabled = currentPage < totalPages - 1
            ) {
                Text("下一页")
            }
        }
    }
}

@Composable
private fun SuggestScoreCard(
    item: SuggestItem,
    illustrationUrl: String?,
    onSuggestionClick: (String, Difficulty?) -> Unit,
    modifier: Modifier = Modifier
) {
    val diffColor = DifficultyColors.forDifficulty(item.difficulty)

    val ccText = remember(item.chartConstant, item.difficulty) {
        "${DifficultyColors.labelFor(item.difficulty)} ${item.chartConstant.formatOne()}"
    }
    val rating = remember(item.currentScore, item.isFullCombo) {
        item.currentScore?.let { ScoreRating.fromScore(it, item.isFullCombo) }
    }
    val currentAccText = remember(item.currentAcc) { item.currentAcc?.let { "${it.formatTwo()}%" } ?: "暂无" }
    val targetAccText = remember(item.targetAcc) { "${item.targetAcc.formatTwo()}%" }
    val currentRksText = remember(item.currentRks) { item.currentRks.formatFour() }
    val potentialRksText = remember(item.potentialRks) { item.potentialRks.formatFour() }
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
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSuggestionClick(item.songId, item.difficulty) },
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

                    if (rating != null) {
                        ScoreRatingTag(rating = rating, fontSize = 10.sp)
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
