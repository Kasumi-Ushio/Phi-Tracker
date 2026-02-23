package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsTab(
    songs: List<SongInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    availableChapters: List<String>,
    selectedChapter: String?,
    onChapterSelect: (String?) -> Unit,
    selectedDifficulty: Difficulty?,
    onDifficultySelect: (Difficulty?) -> Unit,
    minLevel: Int,
    maxLevel: Int,
    onLevelRangeSelect: (Int, Int) -> Unit,
    showFilterSheet: Boolean,
    onToggleFilterSheet: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    getIllustrationUrl: (String) -> String?,
    onSongClick: (String) -> Unit,
    tip: String = "",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("全部曲目 (${songs.size})")
                    if (tip.isNotBlank()) {
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.75f)
                        )
                    }
                }
            }
        )

        // 搜索栏与筛选按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onToggleFilterSheet(true) },
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (selectedChapter != null || selectedDifficulty != null || minLevel > 1 || maxLevel < 16)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Icon(
                    Icons.Filled.FilterList, 
                    contentDescription = "Filter",
                    tint = if (selectedChapter != null || selectedDifficulty != null || minLevel > 1 || maxLevel < 16)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                songs,
                key = { it.id },
                contentType = { "song_item" }
            ) { song ->
                SongItem(
                    song = song,
                    illustrationUrl = getIllustrationUrl(song.id),
                    onSongClick = onSongClick
                )
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onToggleFilterSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            FilterBottomSheetContent(
                availableChapters = availableChapters,
                selectedChapter = selectedChapter,
                onChapterSelect = onChapterSelect,
                selectedDifficulty = selectedDifficulty,
                onDifficultySelect = onDifficultySelect,
                minLevel = minLevel,
                maxLevel = maxLevel,
                onLevelRangeSelect = onLevelRangeSelect,
                onResetFilters = onResetFilters
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheetContent(
    availableChapters: List<String>,
    selectedChapter: String?,
    onChapterSelect: (String?) -> Unit,
    selectedDifficulty: Difficulty?,
    onDifficultySelect: (Difficulty?) -> Unit,
    minLevel: Int,
    maxLevel: Int,
    onLevelRangeSelect: (Int, Int) -> Unit,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("筛选曲目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onResetFilters) {
                Text("重置")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("难度", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("定数范围 (Level): $minLevel - $maxLevel", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        var sliderPosition by remember(minLevel, maxLevel) { mutableStateOf(minLevel.toFloat()..maxLevel.toFloat()) }
        RangeSlider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { 
                onLevelRangeSelect(sliderPosition.start.roundToInt(), sliderPosition.endInclusive.roundToInt()) 
            },
            valueRange = 1f..16f,
            steps = 14 // 16 - 1 = 15 => points minus 1 => 14 steps between them
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), 
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("16", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("章节", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedChapter == null,
                onClick = { onChapterSelect(null) },
                label = { Text("全部") }
            )
            availableChapters.forEach { chapter ->
                FilterChip(
                    selected = selectedChapter == chapter,
                    onClick = { onChapterSelect(chapter) },
                    label = { Text(chapter) }
                )
            }
        }
    }
}

@Composable
fun SongItem(
    song: SongInfo,
    illustrationUrl: String?,
    onSongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(illustrationUrl) {
        illustrationUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(168)
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
                        val ccText = remember(cc) {
                            "${DifficultyColors.labelFor(diff)} ${String.format("%.1f", cc)}"
                        }
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
