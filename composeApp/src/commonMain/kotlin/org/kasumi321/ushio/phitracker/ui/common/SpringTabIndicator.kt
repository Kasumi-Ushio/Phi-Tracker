package org.kasumi321.ushio.phitracker.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Spring-animated tab indicator that follows a [PagerState] during drag/settle.
 *
 * Used with [androidx.compose.foundation.pager.HorizontalPager] backed tab rows.
 * The indicator position and width are derived from tab [positions] and animated
 * with a spring spec for a responsive, "follow-the-finger" feel.
 *
 * @param pagerState the pager state driving the indicator position
 * @param positions list of tab positions from TabRow's indicator lambda
 * @param height height of the indicator bar
 */
@Composable
fun SpringPagerIndicator(
    pagerState: PagerState,
    positions: List<androidx.compose.material3.TabPosition>,
    height: Dp = 3.dp
) {
    val density = LocalDensity.current

    val leftAnim = remember { Animatable(0f) }
    val rightAnim = remember { Animatable(0f) }

    // Calculate target positions from pager state
    val currentPage = pagerState.currentPage
    val pageOffset = pagerState.currentPageOffsetFraction
    val targetFraction = currentPage + pageOffset

    val targetLeftPx: Float
    val targetRightPx: Float

    if (positions.isNotEmpty()) {
        val clampedFraction = targetFraction.coerceIn(0f, (positions.size - 1).toFloat())
        val leftIndex = clampedFraction.toInt().coerceIn(0, positions.size - 1)
        val rightIndex = (leftIndex + 1).coerceAtMost(positions.size - 1)
        val ratio = clampedFraction - leftIndex

        val leftPos = positions[leftIndex]
        val rightPos = positions[rightIndex]

        targetLeftPx = with(density) {
            (leftPos.left.toPx() + (rightPos.left.toPx() - leftPos.left.toPx()) * ratio).coerceAtLeast(0f)
        }
        targetRightPx = with(density) {
            (leftPos.right.toPx() + (rightPos.right.toPx() - leftPos.right.toPx()) * ratio).coerceAtMost(
                positions.last().right.toPx()
            )
        }
    } else {
        targetLeftPx = 0f
        targetRightPx = 0f
    }

    LaunchedEffect(targetLeftPx, targetRightPx) {
        launch {
            leftAnim.animateTo(
                targetValue = targetLeftPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            rightAnim.animateTo(
                targetValue = targetRightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val indicatorWidth = with(density) { (rightAnim.value - leftAnim.value).toDp() }
    val indicatorOffset = with(density) { leftAnim.value.toDp() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(height)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall
                )
        )
    }
}

/**
 * Spring-animated tab indicator for simple (non-pager) tab rows.
 *
 * Animates between tab positions using a spring spec when [selectedTabIndex] changes.
 *
 * @param selectedTabIndex the currently selected tab index
 * @param positions list of tab positions from TabRow's indicator lambda
 * @param height height of the indicator bar
 */
@Composable
fun SpringTabIndicator(
    selectedTabIndex: Int,
    positions: List<androidx.compose.material3.TabPosition>,
    height: Dp = 3.dp
) {
    if (positions.isEmpty()) return

    val density = LocalDensity.current
    val targetPosition = positions[selectedTabIndex.coerceIn(0, positions.size - 1)]

    val leftAnim = remember { Animatable(with(density) { targetPosition.left.toPx() }) }
    val rightAnim = remember { Animatable(with(density) { targetPosition.right.toPx() }) }

    val targetLeftPx = with(density) { targetPosition.left.toPx() }
    val targetRightPx = with(density) { targetPosition.right.toPx() }

    LaunchedEffect(targetLeftPx) {
        leftAnim.animateTo(
            targetValue = targetLeftPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(targetRightPx) {
        rightAnim.animateTo(
            targetValue = targetRightPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val indicatorWidth = with(density) { (rightAnim.value - leftAnim.value).toDp() }
    val indicatorOffset = with(density) { leftAnim.value.toDp() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(height)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall
                )
        )
    }
}
