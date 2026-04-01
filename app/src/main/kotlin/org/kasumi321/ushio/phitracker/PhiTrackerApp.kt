package org.kasumi321.ushio.phitracker

import android.app.Application
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.notificationConfiguration
import org.acra.data.StringFormat
import org.kasumi321.ushio.phitracker.utils.ReleaseCrashNotifier
import timber.log.Timber

@HiltAndroidApp
class PhiTrackerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            // Debug: 采集本地崩溃报告，供用户手动导出反馈
            val acraConfig = CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig::class.java)
                .withReportFormat(StringFormat.JSON)
                .withPluginConfigurations(
                    notificationConfiguration {
                        title = getString(R.string.acra_notification_title)
                        text = getString(R.string.acra_notification_text)
                        channelName = getString(R.string.acra_notification_channel_name)
                        channelDescription = getString(R.string.acra_notification_channel_description)
                        sendButtonText = getString(R.string.acra_notification_send)
                        discardButtonText = getString(R.string.acra_notification_discard)
                        channelImportance = NotificationManager.IMPORTANCE_DEFAULT
                        resIcon = R.mipmap.ic_launcher
                        sendOnClick = false
                    }
                )
            ACRA.init(this, acraConfig)
        } else {
            // Release: 不采集崩溃报告，仅提示用户使用 Debug 版本复现问题
            val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { ReleaseCrashNotifier.notifyCrash(this) }
                systemHandler?.uncaughtException(thread, throwable)
            }
        }

        // Timber 日志 - 仅 debug 模式
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("PhiTrackerApp initialized")
    }

    /**
     * 自定义 Coil ImageLoader
     *
     * 性能优化:
     * - 内存缓存: 应用最大内存的 25%
     * - 磁盘缓存: 100MB → 避免重复下载曲绘
     * - crossfade: 200ms 平滑过渡
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(200)
            .respectCacheHeaders(false) // GitHub 图片不需要缓存头验证
            .build()
    }
}
