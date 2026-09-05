package org.kasumi321.ushio.phitracker.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
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
import org.kasumi321.ushio.phitracker.data.platform.saveArtworkToPictures
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagCategoryDisplay
import org.kasumi321.ushio.phitracker.domain.model.ChartTagVoteCount
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.ui.common.SpringPagerIndicator
import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import org.kasumi321.ushio.phitracker.ui.components.ScoreRatingTag
import org.kasumi321.ushio.phitracker.ui.glass.GlassCapsule
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
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
    syncHistory: List<SongSyncHistoryEntry> = emptyList(),
    apiEnabled: Boolean = false,
    useApiData: Boolean = false,
    apiRequestKey: String = "",
    getSongApiDetail: (Difficulty) -> SongApiDetailState = { SongApiDetailState() },
    onLoadSongApiDetail: (Difficulty) -> Unit = {},
    getChartTags: (Difficulty) -> ChartTagUiState = { ChartTagUiState() },
    onLoadChartTags: (Difficulty) -> Unit = {},
    canVote: Boolean = false,
    onSubmitChartTagVote: (Difficulty, List<String>, List<String>) -> Unit = { _, _, _ -> },
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

    LaunchedEffect(apiEnabled, useApiData, apiRequestKey, selectedDifficulty) {
        if (apiEnabled && useApiData) {
            onLoadSongApiDetail(selectedDifficulty)
        }
    }

    LaunchedEffect(apiRequestKey, selectedDifficulty) {
        onLoadChartTags(selectedDifficulty)
    }

    // Page-level HazeState, independent from the home one. The detail content is
    // the haze source; the info header slides up behind the progressive glass
    // top bar as the difficulty page scrolls, so the blur always has real
    // moving content to sample instead of being a plain color swap.
    val detailHazeState = rememberHazeState()
    val detailGlassStyle = rememberGlassHazeStyle()
    val contentScrollState = rememberScrollState()
    var infoHeaderHeightPx by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            GlassTopBar(hazeState = detailHazeState, style = detailGlassStyle) {
                TopAppBar(
                    title = { Text("曲目详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .hazeSource(state = detailHazeState)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))

            // Collapsing header: the layout shrinks while the content translates
            // up behind the glass bar; no clip so the sliding header stays
            // visible under the bar until fully collapsed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val offset = contentScrollState.value.coerceIn(0, infoHeaderHeightPx)
                        layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
                            placeable.placeRelative(0, -offset)
                        }
                    }
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { infoHeaderHeightPx = it.height }
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
                    if (songInfo.nicknames.isNotEmpty()) {
                        Text(
                            text = "别名: ${songInfo.nicknames.joinToString("、")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            }

            if (availableDifficulties.isNotEmpty()) {
                DifficultyTabRow(
                    pagerState = pagerState,
                    availableDifficulties = availableDifficulties,
                    difficulties = songInfo.difficulties
                )

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
                        chartTagState = getChartTags(pageDifficulty),
                        canVote = canVote,
                        onSubmitChartTagVote = onSubmitChartTagVote,
                        syncHistory = syncHistory,
                        // Shared across pages so the info header collapse follows
                        // whichever difficulty page the user is scrolling
                        scrollState = contentScrollState
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
                // Dialog-local HazeState: the illustration is the source, the
                // action buttons float on a glass capsule above it
                val previewHazeState = rememberHazeState()
                val previewGlassStyle = rememberGlassHazeStyle()
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
                            .hazeSource(state = previewHazeState)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                }
                            }
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentScale = ContentScale.Fit
                    )

                    GlassCapsule(
                        hazeState = previewHazeState,
                        style = previewGlassStyle,
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
    chartTagState: ChartTagUiState,
    canVote: Boolean,
    onSubmitChartTagVote: (Difficulty, List<String>, List<String>) -> Unit,
    syncHistory: List<SongSyncHistoryEntry>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = record.score.formatScore(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "RKS: ${record.rks.formatFourDecimals()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "社区统计数据",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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

        // Chart-tag reads are public, so the section shows regardless of the
        // score-API switch; only voting requires the api_token.
        ChartTagSection(
            state = chartTagState,
            difficulty = difficulty,
            canVote = canVote,
            onSubmitVote = onSubmitChartTagVote
        )
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
                    text = "还没有同步记录\n成绩变动后会显示在这里",
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
private fun SyncHistoryCard(entry: SongSyncHistoryEntry) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChartTagSection(
    state: ChartTagUiState,
    difficulty: Difficulty,
    canVote: Boolean,
    onSubmitVote: (Difficulty, List<String>, List<String>) -> Unit
) {
    var showVoteSheet by remember { mutableStateOf(false) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "谱面标签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            when {
                state.error != null -> Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                state.isLoading -> Unit
                state.categories.all { it.tags.isEmpty() } -> Text(
                    text = "该谱面还没有标签数据，欢迎成为第一个投票的人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> state.categories.filter { it.tags.isNotEmpty() }.forEach { category ->
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        category.tags.forEach { tag -> ChartTagChip(tag) }
                    }
                }
            }

            if (state.voteSucceeded) {
                Text(
                    text = "投票成功，感谢参与！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Voting is only possible with an api_token; without one the
            // button stays hidden instead of failing at submit time.
            if (canVote) {
                OutlinedButton(
                    onClick = { showVoteSheet = true },
                    enabled = !state.isLoading && state.error == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("为这张谱面投票")
                }
            }
        }
    }

    if (showVoteSheet) {
        ChartTagVoteSheet(
            state = state,
            onDismiss = { showVoteSheet = false },
            onSubmit = { primary, secondary ->
                onSubmitVote(difficulty, primary, secondary)
            }
        )
    }
}

@Composable
private fun ChartTagChip(tag: ChartTagVoteCount) {
    Surface(
        color = if (tag.isMine) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "${if (tag.isMine) "✓ " else ""}${tag.name} ${tag.votes}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChartTagVoteSheet(
    state: ChartTagUiState,
    onDismiss: () -> Unit,
    onSubmit: (List<String>, List<String>) -> Unit
) {
    var primaryMode by remember { mutableStateOf(true) }
    var primarySelection by remember {
        mutableStateOf(
            state.allCategories.flatMap { category -> category.tags.filter { it.isMine }.map { it.name } }.toSet()
        )
    }
    var secondarySelection by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(state.voteSucceeded) {
        if (state.voteSucceeded) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "为这张谱面投票",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = primaryMode,
                    onClick = { primaryMode = true },
                    label = { Text("主要印象") }
                )
                FilterChip(
                    selected = !primaryMode,
                    onClick = { primaryMode = false },
                    label = { Text("次要印象") }
                )
            }
            Text(
                text = "主要：最能代表这张谱面的特征；次要：次要特征。点击标签加入当前分组，再次点击已选标签可移除。主题色高亮为主要印象，对比色高亮为次要印象，两组将分别提交。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Constrain the scroll area to the remaining sheet height so the
            // submit button below stays visible even with many tag categories.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.allCategories.forEach { category ->
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        category.tags.forEach { tag ->
                            val inPrimary = tag.name in primarySelection
                            val inSecondary = tag.name in secondarySelection
                            FilterChip(
                                selected = inPrimary || inSecondary,
                                onClick = {
                                    if (primaryMode) {
                                        primarySelection = if (inPrimary) {
                                            primarySelection - tag.name
                                        } else {
                                            primarySelection + tag.name
                                        }
                                        secondarySelection = secondarySelection - tag.name
                                    } else {
                                        secondarySelection = if (inSecondary) {
                                            secondarySelection - tag.name
                                        } else {
                                            secondarySelection + tag.name
                                        }
                                        primarySelection = primarySelection - tag.name
                                    }
                                },
                                label = { Text(tag.name) },
                                // Primary and secondary picks keep separate
                                // highlights so the two groups stay readable
                                // at a glance before they are submitted apart.
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when {
                                        inPrimary -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    selectedLabelColor = when {
                                        inPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            )
                        }
                    }
                }
            }

            state.voteError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { onSubmit(primarySelection.toList(), secondarySelection.toList()) },
                enabled = !state.voteSubmitting && (primarySelection.isNotEmpty() || secondarySelection.isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.voteSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.voteSubmitting) "提交中..." else "提交投票")
            }
        }
    }
}

/**
 * Difficulty tabs above the score pager, with a spring-animated indicator
 * that interpolates across tab positions while swiping.
 */
@Composable
private fun DifficultyTabRow(
    pagerState: androidx.compose.foundation.pager.PagerState,
    availableDifficulties: List<Difficulty>,
    difficulties: Map<Difficulty, Float>
) {
    PrimaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier = Modifier.fillMaxWidth(),
        indicator = {
            SpringPagerIndicator(pagerState = pagerState)
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
                    Text(text = "${diff.name} ${difficulties[diff] ?: ""}")
                }
            )
        }
    }
}
