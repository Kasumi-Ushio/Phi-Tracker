package org.kasumi321.ushio.phitracker.data.platform

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path

private const val CoilCacheMaxSizeBytes = 100L * 1024 * 1024

@Composable
fun ConfigureCoilImageLoader() {
    setSingletonImageLoaderFactory { context ->
        createPhiTrackerImageLoader(context)
    }
}

private fun createPhiTrackerImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(coilCacheDirectory(context))
                .maxSizeBytes(CoilCacheMaxSizeBytes)
                .build()
        }
        .crossfade(200)
        .build()
}

expect fun coilCacheDirectory(context: PlatformContext): Path
