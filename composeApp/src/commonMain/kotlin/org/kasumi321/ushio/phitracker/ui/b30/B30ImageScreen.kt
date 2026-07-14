package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.preloadIllustrationThumbnail
import org.kasumi321.ushio.phitracker.ui.home.scoreCardThumbnailSizePx
import org.kasumi321.ushio.phitracker.data.platform.rememberB30BackgroundPicker
import org.kasumi321.ushio.phitracker.data.platform.saveB30ImageToPictures
import org.kasumi321.ushio.phitracker.data.platform.shareB30Image
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun B30ImageScreen(
    b30: List<BestRecord>,
    displayRks: Float,
    nickname: String,
    challengeModeRank: Int = 0,
    moneyString: String = "",
    clearCounts: Map<String, Int> = emptyMap(),
    fcCount: Int = 0,
    phiCount: Int = 0,
    avatarUri: String? = null,
    showB30Overflow: Boolean = false,
    overflowCount: Int = 9,
    themeSettings: PhiTrackerThemeSettings = PhiTrackerThemeSettings(),
    getLowIllustrationUrl: (String) -> String? = { null },
    getStandardIllustrationUrl: (String) -> String = { "" },
    onBack: () -> Unit
) {
    var export by remember { mutableStateOf<B30ImageExport?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var generationFailed by remember { mutableStateOf(false) }
    var zoomFactor by remember { mutableFloatStateOf(1f) }
    var backgroundMode by remember { mutableStateOf<B30BackgroundMode>(B30BackgroundMode.Auto) }
    var customBackgroundUri by remember { mutableStateOf<String?>(null) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val exportDarkTheme = when (themeSettings.themeMode) {
        1 -> false
        2, 3 -> true
        else -> systemDark
    }
    val exportAmoled = themeSettings.themeMode == 3

    val pickBackground = rememberB30BackgroundPicker { uri ->
        if (uri != null) {
            AppLogger.event("b30_export", "background_selected", mapOf("type" to "custom", "uriPresent" to "true"))
            customBackgroundUri = uri
            backgroundMode = B30BackgroundMode.Custom(uri)
        } else {
            AppLogger.event("b30_export", "background_picker_cancelled", mapOf("type" to "custom"))
        }
    }

    val distinctSongs = remember(b30) {
        b30.distinctBy { it.songId }.map { it.songId to it.songName }
    }

    val exportData = remember(
        b30, displayRks, nickname, challengeModeRank, moneyString,
        clearCounts, fcCount, phiCount, avatarUri,
        showB30Overflow, overflowCount, getLowIllustrationUrl,
        getStandardIllustrationUrl, backgroundMode, customBackgroundUri,
        exportDarkTheme, exportAmoled, themeSettings
    ) {
        val dateText = runCatching {
            val now = Clock.System.now()
            val localDt = now.toLocalDateTime(TimeZone.currentSystemDefault())
            "${localDt.year.toString().padStart(4, '0')}." +
                "${(localDt.month.ordinal + 1).toString().padStart(2, '0')}." +
                "${localDt.dayOfMonth.toString().padStart(2, '0')} " +
                "${localDt.hour.toString().padStart(2, '0')}:" +
                "${localDt.minute.toString().padStart(2, '0')}:" +
                "${localDt.second.toString().padStart(2, '0')}"
        }.getOrDefault("")

        val resolvedBg = resolveBackgroundUri(
            mode = backgroundMode,
            exportData = B30ExportDataBuilder.build(
                b30 = b30,
                displayRks = displayRks,
                nickname = nickname,
                challengeModeRank = challengeModeRank,
                moneyString = moneyString,
                showB30Overflow = showB30Overflow,
                overflowCount = overflowCount,
                illustrationProvider = getLowIllustrationUrl,
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount,
                avatarUri = avatarUri,
                backgroundUri = null,
                dateText = dateText,
                darkTheme = exportDarkTheme,
                isAmoled = exportAmoled,
                themeSettings = themeSettings
            ),
            standardIllustrationProvider = getStandardIllustrationUrl
        )

        B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = displayRks,
            nickname = nickname,
            challengeModeRank = challengeModeRank,
            moneyString = moneyString,
            showB30Overflow = showB30Overflow,
            overflowCount = overflowCount,
            illustrationProvider = getLowIllustrationUrl,
            clearCounts = clearCounts,
            fcCount = fcCount,
            phiCount = phiCount,
            avatarUri = avatarUri,
            backgroundUri = resolvedBg,
            dateText = dateText,
            darkTheme = exportDarkTheme,
            isAmoled = exportAmoled,
            themeSettings = themeSettings
        )
    }

    LaunchedEffect(exportData) {
        isGenerating = true
        generationFailed = false
        zoomFactor = 1f
        export = null
        AppLogger.event(
            "b30_export",
            "generate_started",
            mapOf(
                "cards" to b30.size.toString(),
                "background" to backgroundMode::class.simpleName.orEmpty(),
                "darkTheme" to exportDarkTheme.toString()
            )
        )
        val result = runCatching {
            withContext(Dispatchers.Default) {
                preloadB30ExportImages(exportData)
                B30ImageGenerator.generate(exportData)
            }
        }
        export = result.getOrNull()
        result.getOrNull()?.let { exp ->
            AppLogger.event(
                "b30_export",
                "generate_success",
                mapOf("width" to exp.width.toString(), "height" to exp.height.toString())
            )
        }
        result.exceptionOrNull()?.let { throwable ->
            AppLogger.event("b30_export", "generate_failed", mapOf("error" to (throwable.message ?: "unknown")))
            AppLogger.e("B30ImageScreen", "B30 image generation failed", throwable)
        }
        generationFailed = result.isFailure
        isGenerating = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B30 图片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showBackgroundDialog = true }) {
                        Icon(Icons.Filled.Image, contentDescription = "选择背景")
                    }
                }
            )
        },
        bottomBar = {
            if (export != null && !isGenerating) {
                Surface(tonalElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val exp = export ?: return@Button
                                coroutineScope.launch {
                                    val fileName = buildB30ExportFilename(nickname)
                                    val result = saveB30ImageToPictures(exp.pngBytes, fileName)
                                    AppLogger.event(
                                        "b30_export",
                                        if (result.isSuccess) "save_success" else "save_failed",
                                        mapOf("fileName" to fileName)
                                    )
                                    showPlatformMessage(
                                        if (result.isSuccess) "已保存到 Pictures/PhiTracker" else "保存失败"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Text("  保存", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = {
                                val exp = export ?: return@OutlinedButton
                                coroutineScope.launch {
                                    val fileName = buildB30ExportFilename(nickname)
                                    val result = shareB30Image(exp.pngBytes, fileName)
                                    AppLogger.event(
                                        "b30_export",
                                        if (result.isSuccess) "share_success" else "share_failed",
                                        mapOf("fileName" to fileName)
                                    )
                                    if (result.isFailure) {
                                        showPlatformMessage("分享失败")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text("  分享", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在生成 B30 图片...")
                    }
                }
            } else if (generationFailed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B30 图片生成失败")
                }
            } else {
                export?.let { exp ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(exp.preview) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    zoomFactor = (zoomFactor * zoom).coerceIn(1f, 2.5f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = exp.preview,
                            contentDescription = "B30 预览",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomFactor,
                                    scaleY = zoomFactor
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }

    if (showBackgroundDialog) {
        val selectedSongId = (backgroundMode as? B30BackgroundMode.SongBackground)?.songId
        BackgroundPickerDialog(
            distinctSongs = distinctSongs,
            selectedSongId = selectedSongId,
            getLowIllustrationUrl = getLowIllustrationUrl,
            onSelectDefault = {
                AppLogger.event("b30_export", "background_selected", mapOf("type" to "auto"))
                backgroundMode = B30BackgroundMode.Auto
                customBackgroundUri = null
                showBackgroundDialog = false
            },
            onSelectAlbum = {
                AppLogger.event("b30_export", "background_picker_opened", mapOf("type" to "album"))
                showBackgroundDialog = false
                pickBackground()
            },
            onSelectSong = { songId ->
                AppLogger.event("b30_export", "background_selected", mapOf("type" to "song", "songId" to songId))
                backgroundMode = B30BackgroundMode.SongBackground(songId)
                customBackgroundUri = null
                showBackgroundDialog = false
            },
            onDismiss = { showBackgroundDialog = false }
        )
    }
}

private suspend fun preloadB30ExportImages(exportData: B30ExportData) {
    // The off-screen capture waits only a short beat after composition, so every
    // illustration must resolve from Coil's memory cache synchronously on the
    // first frame. That only happens when the warmed entry's key matches the
    // consumer's request exactly: the export cards request
    // scoreCardThumbnailSizePx(0.9) px with software bitmaps (allowHardware=false,
    // because the layout is drawn onto a software Canvas). Preloading with a
    // different size — or a hardware bitmap — leaves the cache "warm" on disk yet
    // misses in memory, and the thumbnails still capture blank.
    val cardThumbnailPx = scoreCardThumbnailSizePx(B30_EXPORT_CARD_THUMBNAIL_SCALE)
    val cardUris = buildList {
        exportData.phiRecords.forEach { add(it.illustrationUri) }
        exportData.bestRecords.forEach { add(it.illustrationUri) }
        exportData.overflowRecords.forEach { add(it.illustrationUri) }
    }
        .mapNotNull { it?.takeIf(String::isNotBlank) }
        .distinct()

    // Avatar/background are consumed differently (natural-size avatar; the
    // background is re-decoded and blurred by the platform generator), so an
    // exact memory-cache hit isn't achievable — warming them as software bitmaps
    // still spares the capture a network round-trip.
    val auxiliaryUris = listOfNotNull(
        exportData.avatarUri?.takeIf(String::isNotBlank),
        exportData.backgroundUri?.takeIf(String::isNotBlank)
    ).distinct()

    if (cardUris.isEmpty() && auxiliaryUris.isEmpty()) return

    coroutineScope {
        val semaphore = Semaphore(6)
        val tasks = buildList {
            cardUris.forEach { uri ->
                add(uri to async {
                    semaphore.withPermit {
                        preloadIllustrationThumbnail(uri, size = cardThumbnailPx, allowHardware = false)
                    }
                })
            }
            auxiliaryUris.forEach { uri ->
                add(uri to async {
                    semaphore.withPermit {
                        preloadIllustrationThumbnail(uri, allowHardware = false)
                    }
                })
            }
        }
        tasks.forEach { (uri, task) ->
            task.await().onFailure { throwable ->
                AppLogger.w(
                    "B30ImageScreen",
                    "B30 export image preload failed: uri=$uri error=${throwable.message ?: throwable::class.simpleName}"
                )
            }
        }
    }
}

@Composable
private fun BackgroundPickerDialog(
    distinctSongs: List<Pair<String, String>>,
    selectedSongId: String?,
    getLowIllustrationUrl: (String) -> String?,
    onSelectDefault: () -> Unit,
    onSelectAlbum: () -> Unit,
    onSelectSong: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val platformContext = LocalPlatformContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("选择背景", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSelectDefault) { Text("默认背景") }
                    OutlinedButton(onClick = onSelectAlbum) { Text("相册图片") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(360.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(distinctSongs, key = { it.first }) { (songId, songName) ->
                        val isSelected = selectedSongId == songId
                        SongGridItem(
                            songId = songId,
                            songName = songName,
                            getLowIllustrationUrl = getLowIllustrationUrl,
                            platformContext = platformContext,
                            isSelected = isSelected,
                            onClick = { onSelectSong(songId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongGridItem(
    songId: String,
    songName: String,
    getLowIllustrationUrl: (String) -> String?,
    platformContext: coil3.PlatformContext,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val thumbUrl = remember(songId) { getLowIllustrationUrl(songId) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .let { mod -> if (thumbUrl != null) mod.clickable { onClick() } else mod }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (thumbUrl != null) {
                val imageRequest = remember(platformContext, thumbUrl) {
                    ImageRequest.Builder(platformContext)
                        .data(thumbUrl)
                        .size(144)
                        .networkCachePolicy(CachePolicy.READ_ONLY)
                        .crossfade(150)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = songName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = songName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
