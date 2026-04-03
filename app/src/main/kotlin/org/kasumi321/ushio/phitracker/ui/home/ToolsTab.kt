package org.kasumi321.ushio.phitracker.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator

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
        onFetchRankByUser: () -> Unit,
        onFetchRankByPosition: (Int) -> Unit,
        onFetchRksRank: (Float) -> Unit,
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
                modifier =
                        modifier.fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = 16.dp)
                                .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            // Section 1: 等效 RKS 计算器
            CollapsibleToolCard(
                    title = "等效 RKS 计算器",
                    subtitle = "根据定数和准确率计算等效 RKS",
                    icon = Icons.Default.Calculate
            ) { RksCalculatorContent() }

            // Section 2: RKS 历史变化
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

            // Section 3: 导出 sessionToken
            CollapsibleToolCard(
                    title = "sessionToken 导出",
                    subtitle = "查看并复制当前登录凭证",
                    icon = Icons.Default.ContentCopy
            ) { SessionTokenContent(sessionToken) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════
// 可折叠工具卡片
// ══════════════════════════════════════════════════════════════
@Composable
private fun CollapsibleToolCard(
        title: String,
        subtitle: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        // 标题行（可点击展开/折叠）
        Row(
                modifier =
                        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
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
                    imageVector =
                            if (expanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 展开内容
        AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Section 1: RKS 计算器内容
// ══════════════════════════════════════════════════════════════
@Composable
private fun RksCalculatorContent() {
    var chartConstantInput by rememberSaveable { mutableStateOf("") }
    var accuracyInput by rememberSaveable { mutableStateOf("") }

    val chartConstant = chartConstantInput.toFloatOrNull()
    val accuracy = accuracyInput.toFloatOrNull()

    val resultRks =
            if (chartConstant != null &&
                            accuracy != null &&
                            accuracy in 0f..100f &&
                            chartConstant >= 0f
            ) {
                RksCalculator.calculateSingleRks(accuracy, chartConstant)
            } else null

    Text(
            text = "计算公式：RKS = ((acc − 55) / 45)² × 定数",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 输入框并排放置
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

    // 计算结果 — 左对齐
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
                text = "计算结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
                text = if (resultRks != null) "%.4f".format(resultRks) else "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color =
                        if (resultRks != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ══════════════════════════════════════════════════════════════
// Section 2: RKS 历史折线图内容
// ══════════════════════════════════════════════════════════════
@Composable
private fun RksHistoryChartContent(snapshots: List<SyncSnapshotEntity>) {
    if (snapshots.size < 2) {
        Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text =
                            if (snapshots.isEmpty()) "暂无同步记录\n同步存档后，数据将在此处展示"
                            else "需要至少 2 次同步记录才能绘制趋势图",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
        }
        return
    }

    // 按时间排序（旧 → 新）
    val sorted = remember(snapshots) { snapshots.sortedBy { it.timestamp } }
    val minRks = remember(sorted) { sorted.minOf { it.rks } }
    val maxRks = remember(sorted) { sorted.maxOf { it.rks } }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 折线图
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val paddingLeft = 96f // 增大左侧留白以避免坐标轴与折线重合
        val paddingRight = 16f
        val paddingTop = 16f
        val paddingBottom = 32f
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        val rksRange = (maxRks - minRks).coerceAtLeast(0.1f)
        val yMin = minRks - rksRange * 0.1f
        val yMax = maxRks + rksRange * 0.1f
        val yRange = yMax - yMin

        // 网格线 (3 条)
        for (i in 0..2) {
            val y = paddingTop + chartHeight * (1f - i / 2f)
            val rksValue = yMin + yRange * (i / 2f)
            drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, y),
                    end = Offset(paddingLeft + chartWidth, y),
                    strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                    "%.2f".format(rksValue),
                    4f,
                    y + 5f,
                    android.graphics.Paint().apply {
                        color = textColor.hashCode()
                        textSize = 22f
                        isAntiAlias = true
                    }
            )
        }

        // 折线
        val path = Path()
        sorted.forEachIndexed { index, snapshot ->
            val x = paddingLeft + chartWidth * index / (sorted.size - 1).toFloat()
            val y = paddingTop + chartHeight * (1f - (snapshot.rks - yMin) / yRange)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // 数据点
        sorted.forEachIndexed { index, snapshot ->
            val x = paddingLeft + chartWidth * index / (sorted.size - 1).toFloat()
            val y = paddingTop + chartHeight * (1f - (snapshot.rks - yMin) / yRange)
            drawCircle(lineColor, radius = 5f, center = Offset(x, y))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 同步历史列表（最近 10 条，新 → 旧）
    val recent = remember(snapshots) { snapshots.sortedByDescending { it.timestamp }.take(10) }
    val sdf = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    recent.forEachIndexed { index, snapshot ->
        val prevRks = if (index + 1 < recent.size) recent[index + 1].rks else null
        val delta = if (prevRks != null) snapshot.rks - prevRks else null

        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text = sdf.format(Date(snapshot.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (delta != null && delta != 0f) {
                    Text(
                            text = if (delta > 0) "+%.4f".format(delta) else "%.4f".format(delta),
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
                        text = "%.4f".format(snapshot.rks),
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

// ══════════════════════════════════════════════════════════════
// API: 排行榜（按用户）
// ══════════════════════════════════════════════════════════════
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
    Text(
            text = state.message ?: "尚未查询",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ══════════════════════════════════════════════════════════════
// API: 排行榜（按名次）
// ══════════════════════════════════════════════════════════════
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
    Text(
            text = state.message ?: "尚未查询",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ══════════════════════════════════════════════════════════════
// API: RKS 区间统计
// ══════════════════════════════════════════════════════════════
@Composable
private fun ApiRksRankContent(state: ApiToolResult, defaultRks: Float, onFetch: (Float) -> Unit) {
    var rksInput by
            rememberSaveable(defaultRks) {
                mutableStateOf(if (defaultRks > 0f) "%.4f".format(defaultRks) else "")
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
    Text(
            text = state.message ?: "尚未查询",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ══════════════════════════════════════════════════════════════
// Section 3: Token 导出内容
// ══════════════════════════════════════════════════════════════
@Composable
private fun SessionTokenContent(sessionToken: String?) {
    val context = LocalContext.current
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
                                text =
                                        "sessionToken 是您的账号凭证，拥有此 Token 的人可以读取您的游戏存档。\n\n请勿将此 Token 分享给任何不信任的人。",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    text = sessionToken,
                                    style =
                                            MaterialTheme.typography.bodySmall.copy(
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
                                val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                                ClipboardManager
                                clipboard.setPrimaryClip(
                                        ClipData.newPlainText("sessionToken", sessionToken)
                                )
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
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
