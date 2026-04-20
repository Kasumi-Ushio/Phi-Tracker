package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs

@Composable
fun SpringTabIndicator(
    tabPositions: List<TabPosition>,
    selectedTabIndex: Int,
    pageOffsetFraction: Float = 0f,
    height: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (tabPositions.isEmpty()) return

    val safeIndex = selectedTabIndex.coerceIn(0, tabPositions.lastIndex)
    val current = tabPositions[safeIndex]
    val hasOffset = pageOffsetFraction != 0f
    val candidateTargetIndex = if (pageOffsetFraction > 0f) safeIndex + 1 else safeIndex - 1
    val targetIndex = candidateTargetIndex.coerceIn(0, tabPositions.lastIndex)
    val useTarget = hasOffset && targetIndex != safeIndex
    val fraction = if (useTarget) abs(pageOffsetFraction).coerceIn(0f, 1f) else 0f
    val target = tabPositions[targetIndex]

    val targetLeft = lerp(current.left, target.left, fraction)
    val targetWidth = lerp(current.width, target.width, fraction)

    val animatedLeft = animateDpAsState(
        targetValue = targetLeft,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring_tab_indicator_left"
    )
    val animatedWidth = animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring_tab_indicator_width"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = animatedLeft.value, y = 0.dp)
                .width(animatedWidth.value)
                .background(color)
                .height(height)
        )
    }
}
