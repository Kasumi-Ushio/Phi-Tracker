package org.kasumi321.ushio.phitracker.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import coil.request.CachePolicy
import coil.imageLoader
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.ui.home.SongApiDetailState
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.utils.ImageStorageHelper

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
    getIllustrationUrl: (String) -> String?,
    getStandardIllustrationUrl: (String) -> String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only difficulties that exist for this song
    val availableDifficulties = Difficulty.entries.filter { songInfo.difficulties.containsKey(it) }
    var selectedTabIndex by remember { 
        mutableIntStateOf(availableDifficulties.indexOfFirst { it == Difficulty.IN }.takeIf { it >= 0 } ?: 0) 
    }
    val selectedDifficulty = availableDifficulties.getOrNull(selectedTabIndex) ?: Difficulty.IN
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
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Thumbnail + Meta info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left: Thumbnail
                val context = LocalContext.current
                val imageUrl = getIllustrationUrl(songInfo.id)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .networkCachePolicy(CachePolicy.READ_ONLY)
                        .build(),
                    contentDescription = "Illustration",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showImagePreview = true },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Right: Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
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

            // Difficulty Tabs
            if (availableDifficulties.isNotEmpty()) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableDifficulties.forEachIndexed { index, diff ->
                        val diffColor = DifficultyColors.forDifficulty(diff)
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    text = "${diff.name} ${songInfo.difficulties[diff] ?: ""}"
                                ) 
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Difficulty Details Card
                val charter = songInfo.charters[selectedDifficulty] ?: "未知"
                val notes = songInfo.noteCounts[selectedDifficulty]
                val record = userRecords.find { it.difficulty == selectedDifficulty }

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
                                        text = String.format("%,d", record.score),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (record.accuracy >= 100f) {
                                        StatusChip("φ", Color(0xFFFFD54F), Color(0xFF5D4037))
                                    } else if (record.isFullCombo) {
                                        StatusChip("FC", Color(0xFF4FC3F7), Color.White)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format("%.4f%%", record.accuracy),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "RKS: ${String.format("%.4f", record.rks)}",
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
                                        songApiDetail.avgAcc?.let { String.format("%.4f%%", it) } ?: "—"
                                    }（由 ${songApiDetail.avgAccCount ?: 0} 个样本取得）"
                                )
                                Text(
                                    text = "拟合定数: ${
                                        songApiDetail.fittedDifficulty?.let { String.format("%.4f", it) } ?: "—"
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

                // 同步历史记录
                val currentHistory = if (apiEnabled && useApiData) songApiDetail.history else syncHistory
                val filteredHistory = currentHistory
                    .filter { it.difficulty == selectedDifficulty.name }
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
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    var isDownloading by remember { mutableStateOf(false) }

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(standardUrl)
                            .crossfade(true)
                            .build(),
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

                    // Top controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(onClick = {
                            isDownloading = true
                            coroutineScope.launch {
                                try {
                                    val request = ImageRequest.Builder(context)
                                        .data(standardUrl)
                                        .allowHardware(false)
                                        .build()
                                    val result = (context.imageLoader.execute(request) as coil.request.SuccessResult).drawable
                                    val bitmap = (result as android.graphics.drawable.BitmapDrawable).bitmap
                                    val path = ImageStorageHelper.saveToPictures(
                                        context,
                                        bitmap,
                                        "${songInfo.id.replace(".", "_")}_hq.png"
                                    )
                                    if (path != null) {
                                        Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isDownloading = false
                                }
                            }
                        }, enabled = !isDownloading) {
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
private fun StatusChip(text: String, bgColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

/**
 * 同步历史卡片
 */
@Composable
private fun SyncHistoryCard(entry: SongSyncHistoryEntity) {
    val formattedTime = remember(entry.timestamp) {
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(entry.timestamp))
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
                        text = String.format("%,d", entry.score),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (entry.accuracy >= 100f) {
                        StatusChip("φ", Color(0xFFFFD54F), Color(0xFF5D4037))
                    } else if (entry.isFullCombo) {
                        StatusChip("FC", Color(0xFF4FC3F7), Color.White)
                    }
                }
            }
            Text(
                text = String.format("%.4f%%", entry.accuracy),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
