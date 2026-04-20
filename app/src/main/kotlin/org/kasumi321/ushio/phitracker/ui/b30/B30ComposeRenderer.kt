package org.kasumi321.ushio.phitracker.ui.b30

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme

class B30ComposeRenderer(
    private val context: Context
) {
    data class RenderSpec(
        val widthPx: Int = 1800,
        val density: Float = 1f,
        val fontScale: Float = 1f
    )

    suspend fun render(data: B30ExportData, spec: RenderSpec = RenderSpec()): Bitmap = withContext(Dispatchers.Main) {
        val activity = context.findActivity()
            ?: error("B30ComposeRenderer requires an Activity context.")
        val root = activity.window?.decorView as? ViewGroup
            ?: error("Cannot access decorView root for off-screen rendering.")

        val container = FrameLayout(activity).apply {
            alpha = 0f
            translationX = -10_000f
            layoutParams = ViewGroup.LayoutParams(
                spec.widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val composeView = ComposeView(context).apply {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(spec.density, spec.fontScale)
                ) {
                    PhiTrackerTheme {
                        B30ExportLayout(data)
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                spec.widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(container)
        try {
            val widthSpec = MeasureSpec.makeMeasureSpec(spec.widthPx, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, spec.widthPx, composeView.measuredHeight)
            composeView.drawToBitmap()
        } finally {
            root.removeView(container)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
