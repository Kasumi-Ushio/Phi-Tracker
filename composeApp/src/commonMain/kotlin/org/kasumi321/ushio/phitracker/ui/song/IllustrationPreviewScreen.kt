package org.kasumi321.ushio.phitracker.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.platform.saveArtworkToPictures
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import kotlin.math.roundToInt

/**
 * Fullscreen illustration preview as its own navigation destination (see
 * [IllustrationPreviewRoute][org.kasumi321.ushio.phitracker.ui.navigation.IllustrationPreviewRoute]).
 * A dedicated page replaces the old in-composition overlay, so entering and
 * leaving the preview rides the shared navigation transitions instead of
 * popping in abruptly, and the system back gesture simply pops the back stack.
 *
 * The action buttons sit on a translucent dark scrim rather than a glass
 * capsule: the capsule samples the artwork through Haze, which washes the
 * white icons out on light artwork regardless of the app theme. Supports
 * pinch zoom and two-finger rotation; the corner button snaps rotation to
 * 90° steps for inspecting details with the phone held sideways.
 */
@Composable
fun IllustrationPreviewScreen(
    illustrationUrl: String?,
    songId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        var scale by rememberSaveable { mutableFloatStateOf(1f) }
        var rotation by rememberSaveable { mutableFloatStateOf(0f) }
        val coroutineScope = rememberCoroutineScope()
        var isDownloading by remember { mutableStateOf(false) }

        val platformContext = LocalPlatformContext.current
        val previewRequest = remember(platformContext, illustrationUrl) {
            illustrationUrl?.takeIf { it.isNotBlank() }?.let { url ->
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
                    detectTransformGestures { _, _, zoom, rotationDelta ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        rotation += rotationDelta
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation),
            contentScale = ContentScale.Fit
        )

        PreviewButtonCapsule(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = {
                    val standardArtworkUrl = illustrationUrl.orEmpty()
                    if (standardArtworkUrl.isBlank()) {
                        showPlatformMessage("保存失败")
                        return@IconButton
                    }
                    isDownloading = true
                    coroutineScope.launch {
                        val fileName = "${songId.replace(".", "_")}_hq.png"
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
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        PreviewButtonCapsule(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { rotation = (rotation / 90f).roundToInt() * 90f + 90f }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "旋转",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PreviewButtonCapsule(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
