package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.kasumi321.ushio.phitracker.data.platform.saveB30ImageToPictures
import org.kasumi321.ushio.phitracker.data.platform.shareB30Image
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.domain.model.BestRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun B30ImageScreen(
    b30: List<BestRecord>,
    displayRks: Float,
    nickname: String,
    onBack: () -> Unit
) {
    var export by remember { mutableStateOf<B30ImageExport?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var generationFailed by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(0.3f) }
    val coroutineScope = rememberCoroutineScope()

    // 异步生成图片
    LaunchedEffect(b30, displayRks, nickname) {
        isGenerating = true
        generationFailed = false
        export = null
        val result = runCatching {
            withContext(Dispatchers.Default) {
                B30ImageGenerator.generate(b30, displayRks, nickname)
            }
        }
        export = result.getOrNull()
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
                }
            )
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B30 图片生成失败")
                }
            } else {
                export?.let { exp ->
                    // 可缩放预览
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.1f, 2f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = exp.preview,
                            contentDescription = "B30 成绩图",
                            modifier = Modifier.graphicsLayer(
                                scaleX = scale,
                                scaleY = scale
                            )
                        )
                    }

                    // 操作按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val pngBytes = exp.pngBytes
                                val fileName = "B30_${currentTimeMillis()}.png"
                                coroutineScope.launch {
                                    val result = saveB30ImageToPictures(pngBytes, fileName)
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
                                val pngBytes = exp.pngBytes
                                val fileName = "B30_${currentTimeMillis()}.png"
                                coroutineScope.launch {
                                    val result = shareB30Image(pngBytes, fileName)
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
    }
}

@OptIn(ExperimentalTime::class)
private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
