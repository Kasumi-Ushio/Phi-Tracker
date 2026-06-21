package org.kasumi321.ushio.phitracker.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.platform.saveArtworkToPictures
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.ui.common.SpringPagerIndicator
import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import org.kasumi321.ushio.phitracker.ui.components.ScoreRatingTag
import org.kasumi321.ushio.phitracker.ui.home.SongApiDetailState
import kotlin.math.roundToInt
import kotlin.time.Instant

private fun Float.formatFourDecimals(): String {
    val v = (this * 10000).roundToInt()
    return "${v / 10000}.${(kotlin.math.abs(v) % 10000).toString().padStart(4, '0')}"
}

private fun Int.formatScore(): String {
    return this.toString().reversed().chunked(3).joinToString(",").reversed()
}

private fun Long.formatSyncTime(): String {
    val dateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dateTime.month.ordinal + 1
    return "${month.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    songInfo: SongInfo,
    userRecords: List<BestRecord> = emptyList(),
    syncHistory: List<SongSyncHistoryEntity> = emptyList(),
    apiEnabled: Boolean = false,
    useApiData: Boolean = false,
    getSongApiDetail: (Difficulty) -> SongApiDetailState = { SongApiDetailState() },
    onLoadSongApiDetail: (Difficulty) -> Unit = {},
    getLowIllustrationUrl: (String) -> String?,
    getStandardIllustrationUrl: (String) -> String?,
    initialDifficulty: Difficulty? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableDifficulties = Difficulty.entries.filter { songInfo.difficulties.containsKey(it) }
    val defaultTabIndex = availableDifficulties.indexOfFirst { it == Difficulty.IN }.takeIf { it >= 0 } ?: 0
    val initialTabIndex = initialDifficulty?.let { availableDifficulties.indexOf(it) }?.takeIf { it >= 0 }
    val pagerState = rememberPagerState(initialPage = initialTabIndex ?: defaultTabIndex) { availableDifficulties.size }
    val selectedDifficulty = availableDifficulties.getOrNull(pagerState.currentPage) ?: Difficulty.IN
    val songApiDetail = getSongApiDetail(selectedDifficulty)
    var showImagePreview by remember { mutableStateOf(false) }

    LaunchedEffect(apiEnabled, useApiData, selectedDifficulty) {
        if (apiEnabled && useApiData) {
            onLoadSongApiDetail(selectedDifficulty)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("曲目详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                val thumbnailUrl = getLowIllustrationUrl(songInfo.id)
                val platformContext = LocalPlatformContext.current
                val thumbnailRequest = remember(platformContext, thumbnailUrl) {
                    thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        ImageRequest.Builder(platformContext)
                            .data(url)
                            .size(168)
                            .networkCachePolicy(CachePolicy.READ_ONLY)
                            .crossfade(200)
                            .build()
                    }
                }
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = "Illustration",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showImagePreview = true },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songInfo.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "作曲: ${songInfo.composer}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "曲绘: ${songInfo.illustrator}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        InfoChip(label = "BPM", value = songInfo.bpm)
                        InfoChip(label = "时长", value = songInfo.length)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "章节: ${songInfo.chapter}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (availableDifficulties.isNotEmpty()) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                    indicator = { positions ->
                        SpringPagerIndicator(
                            pagerState = pagerState,
                            positions = positions
                        )
                    }
                ) {
                    val scope = rememberCoroutineScope()
                    availableDifficulties.forEachIndexed { index, diff ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(text = "${diff.name} ${songInfo.difficulties[diff] ?: ""}")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = true,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    val pageDifficulty = availableDifficulties[page]
                    DifficultyContent(
                        songInfo = songInfo,
                        difficulty = pageDifficulty,
                        userRecords = userRecords,
                        apiEnabled = apiEnabled,
                        useApiData = useApiData,
                        songApiDetail = getSongApiDetail(pageDifficulty),
                        syncHistory = syncHistory
                    )
                }
            }
        }

        if (showImagePreview) {
            val standardUrl = getStandardIllustrationUrl(songInfo.id)
            Dialog(
                onDismissRequest = { showImagePreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    var scale by remember { mutableFloatStateOf(1f) }
                    val coroutineScope = rememberCoroutineScope()
                    var isDownloading by remember { mutableStateOf(false) }

                    val platformContext = LocalPlatformContext.current
                    val previewRequest = remember(platformContext, standardUrl) {
                        standardUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            ImageRequest.Builder(platformContext)
                                .data(url)
                                .diskCacheKey(url)
                                .crossfade(200)
                                .build()
                        }
                    }
                    AsyncImage(
                        model = previewRequest,
                        contentDescription = "Full Illustration",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                }
                            }
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentScale = ContentScale.Fit
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val standardArtworkUrl = standardUrl.orEmpty()
                                if (standardArtworkUrl.isBlank()) {
                                    showPlatformMessage("保存失败")
                                    return@IconButton
                                }
                                isDownloading = true
                                coroutineScope.launch {
                                    val fileName = "${songInfo.id.replace(".", "_")}_hq.png"
                                    val result = saveArtworkToPictures(standardArtworkUrl, fileName)
                                    showPlatformMessage(
                                        if (result.isSuccess) "已保存到相册" else "保存失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                    )
                                    isDownloading = false
                                }
                            },
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Save,
                                    contentDescription = "Save",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { showImagePreview = false }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyContent(
    songInfo: SongInfo,
    difficulty: Difficulty,
    userRecords: List<BestRecord>,
    apiEnabled: Boolean,
    useApiData: Boolean,
    songApiDetail: SongApiDetailState,
    syncHistory: List<SongSyncHistoryEntity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val charter = songInfo.charters[difficulty] ?: "未知"
        val notes = songInfo.noteCounts[difficulty]
        val record = userRecords.find { it.difficulty == difficulty }

        if (record != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                            text = "单曲成绩",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = record.score.formatScore(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ScoreRatingTag(
                                rating = ScoreRating.fromScore(record.score, record.isFullCombo),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${record.accuracy.formatFourDecimals()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "RKS: ${record.rks.formatFourDecimals()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (apiEnabled && useApiData) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "查分 API 统计信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (songApiDetail.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (songApiDetail.error != null) {
                        Text(
                            text = songApiDetail.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("单曲排名: ${songApiDetail.userRank ?: "—"} / ${songApiDetail.totalUsers ?: "—"}")
                        Text(
                            text = "平均 ACC: ${
                                songApiDetail.avgAcc?.let { "${it.formatFourDecimals()}%" } ?: "—"
                            }（由 ${songApiDetail.avgAccCount ?: 0} 个样本取得）"
                        )
                        Text(
                            text = "拟合定数: ${
                                songApiDetail.fittedDifficulty?.let { it.formatFourDecimals() } ?: "—"
                            }"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "谱面信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "制谱: $charter",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (notes != null && notes.total > 0) {
                    Text(
                        text = "Notes 分布 (Total: ${notes.total})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        NoteStatItem("Tap", notes.tap)
                        NoteStatItem("Drag", notes.drag)
                        NoteStatItem("Hold", notes.hold)
                        NoteStatItem("Flick", notes.flick)
                    }
                } else {
                    Text(
                        text = "暂无 Notes 数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentHistory = if (apiEnabled && useApiData) songApiDetail.history else syncHistory
        val filteredHistory = currentHistory
            .filter { it.difficulty == difficulty.name }
            .take(3)

        if (filteredHistory.isNotEmpty()) {
            Text(
                text = "同步历史",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            filteredHistory.forEach { entry ->
                SyncHistoryCard(entry)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Text(
                    text = "暂无同步历史\n同步后发生变化的成绩将显示在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.takeIf { it.isNotBlank() } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NoteStatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncHistoryCard(entry: SongSyncHistoryEntity) {
    val formattedTime = remember(entry.timestamp) {
        entry.timestamp.formatSyncTime()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.score.formatScore(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ScoreRatingTag(
                        rating = ScoreRating.fromScore(entry.score, entry.isFullCombo),
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                text = "${entry.accuracy.formatFourDecimals()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
