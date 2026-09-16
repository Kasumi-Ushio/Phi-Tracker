package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors
import org.kasumi321.ushio.phitracker.ui.utils.chapterDisplayName
import kotlin.math.roundToInt

private fun Float.formatLevel(): String {
    val v = (this * 10).roundToInt()
    return "${v / 10}.${kotlin.math.abs(v % 10)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsTab(
    state: SongsUiState,
    onSearchChange: (String) -> Unit,
    onToggleChapter: (String) -> Unit,
    onClearChapters: () -> Unit,
    onDifficultySelect: (Difficulty?) -> Unit,
    onLevelRangeSelect: (Int, Int) -> Unit,
    onToggleFilterSheet: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    onRefreshSongData: () -> Unit,
    getIllustrationUrl: (String) -> String?,
    onSongClick: (String, Difficulty?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val songs = state.filteredSongs
    val availableChapters = state.availableChapters
    val selectedChapters = state.selectedChapters
    val selectedDifficulty = state.selectedDifficulty
    val minLevel = state.minLevel
    val maxLevel = state.maxLevel
    val showFilterSheet = state.showFilterSheet

    // Title, tip, search and filter live in the floating SongsHeader; the list
    // scrolls behind it, padded so the first and last songs stay clear
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Keeps the last song clear of the floating glass bottom bar
            bottom = contentPadding.calculateBottomPadding() + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state.songDataUpdateAvailable && !state.isSongDataRefreshing) {
            item(key = "song_data_update_banner", contentType = "song_data_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "曲目数据有更新",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = onRefreshSongData) {
                            Text("立即更新")
                        }
                    }
                }
            }
        }
        items(
            songs,
            key = { it.id },
            contentType = { "song_item" }
        ) { song ->
            SongItem(
                song = song,
                illustrationUrl = getIllustrationUrl(song.id),
                onSongClick = { songId -> onSongClick(songId, null) }
            )
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onToggleFilterSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            FilterBottomSheetContent(
                availableChapters = availableChapters,
                selectedChapters = selectedChapters,
                onToggleChapter = onToggleChapter,
                onClearChapters = onClearChapters,
                selectedDifficulty = selectedDifficulty,
                onDifficultySelect = onDifficultySelect,
                minLevel = minLevel,
                maxLevel = maxLevel,
                onLevelRangeSelect = onLevelRangeSelect,
                onResetFilters = onResetFilters,
                onClose = { onToggleFilterSheet(false) }
            )
        }
    }
}

@Composable
fun SongDataRefreshProgressCard(
    statusText: String,
    progressFraction: Float?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheetContent(
    availableChapters: List<String>,
    selectedChapters: Set<String>,
    onToggleChapter: (String) -> Unit,
    onClearChapters: () -> Unit,
    selectedDifficulty: Difficulty?,
    onDifficultySelect: (Difficulty?) -> Unit,
    minLevel: Int,
    maxLevel: Int,
    onLevelRangeSelect: (Int, Int) -> Unit,
    onResetFilters: () -> Unit,
    onClose: () -> Unit
) {
    var showChapterDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("筛选曲目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onResetFilters) {
                    Text("重置")
                }
                TextButton(onClick = onClose) {
                    Text("完成")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("难度", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedDifficulty == null,
                    onClick = { onDifficultySelect(null) },
                    label = { Text("全部") }
                )
            }
            items(listOf(Difficulty.EZ, Difficulty.HD, Difficulty.IN, Difficulty.AT)) { diff ->
                FilterChip(
                    selected = selectedDifficulty == diff,
                    onClick = { onDifficultySelect(diff) },
                    label = { Text(diff.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DifficultyColors.forDifficulty(diff).copy(alpha = 0.8f),
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("定数范围：$minLevel ~ $maxLevel", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        var sliderPosition by remember(minLevel, maxLevel) { mutableStateOf(minLevel.toFloat()..maxLevel.toFloat()) }
        RangeSlider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                onLevelRangeSelect(sliderPosition.start.roundToInt(), sliderPosition.endInclusive.roundToInt())
            },
            valueRange = 1f..17f,
            steps = 15
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("17", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // The chapter list lives in its own dialog: 35+ chips made the sheet
        // tall enough to fight the sheet's own drag gestures (see Issue 11).
        // This row stays a compact summary and opens ChapterFilterDialog.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showChapterDialog = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("章节", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedChapters.isEmpty()) "点击选择" else "已选 ${selectedChapters.size} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showChapterDialog) {
        ChapterFilterDialog(
            availableChapters = availableChapters,
            selectedChapters = selectedChapters,
            onToggleChapter = onToggleChapter,
            onClearChapters = onClearChapters,
            onDismiss = { showChapterDialog = false }
        )
    }
}

@Composable
private fun ChapterFilterDialog(
    availableChapters: List<String>,
    selectedChapters: Set<String>,
    onToggleChapter: (String) -> Unit,
    onClearChapters: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择章节") },
        text = {
            // A capped inner scroll is safe here: unlike the bottom sheet, a
            // plain dialog has no drag-to-dismiss gesture competing for drags.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                availableChapters.forEach { chapter ->
                    FilterChip(
                        selected = chapter in selectedChapters,
                        onClick = { onToggleChapter(chapter) },
                        label = { Text(chapterDisplayName(chapter)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = if (selectedChapters.isNotEmpty()) {
            {
                TextButton(onClick = onClearChapters) {
                    Text("全部清除 (${selectedChapters.size})")
                }
            }
        } else null
    )
}

@Composable
fun SongItem(
    song: SongInfo,
    illustrationUrl: String?,
    onSongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier.fillMaxWidth()
            .clickable { onSongClick(song.id) },
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
            // 曲绘缩略图
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.composer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 定数标签行
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val orderedDiffs = remember { listOf(Difficulty.EZ, Difficulty.HD, Difficulty.IN, Difficulty.AT) }
                    for (diff in orderedDiffs) {
                        val cc = song.difficulties[diff] ?: continue
                        val ccText = remember(cc) { "${DifficultyColors.labelFor(diff)} ${cc.formatLevel()}" }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DifficultyColors.forDifficulty(diff))
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
                    }
                }
            }
        }
    }
}
