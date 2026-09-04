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
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Spring-animated tab indicators for the TabIndicatorScope-based tab rows
 * ([androidx.compose.material3.PrimaryTabRow] / [androidx.compose.material3.SecondaryTabRow]).
 *
 * The modern [TabIndicatorScope] does not expose tab positions to composition;
 * they are only available inside [TabIndicatorScope.tabIndicatorLayout]'s measure
 * lambda. Both indicators capture them into local state from that lambda and drive
 * the spring from composition, preserving the feel of the previous list-based
 * implementation.
 */

/**
 * Spring-animated indicator that follows a [PagerState] during drag and settle.
 *
 * @param pagerState the pager state driving the indicator position
 * @param height height of the indicator bar
 */
@Composable
fun TabIndicatorScope.SpringPagerIndicator(
    pagerState: PagerState,
    height: Dp = 3.dp
) {
    SpringIndicator(
        targetFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
        height = height
    )
}

/**
 * Spring-animated indicator for simple (non-pager) tab rows.
 *
 * Animates between tab positions using a spring spec when the selection changes.
 *
 * @param selectedTabIndex the currently selected tab index
 * @param height height of the indicator bar
 */
@Composable
fun TabIndicatorScope.SpringTabIndicator(
    selectedTabIndex: Int,
    height: Dp = 3.dp
) {
    SpringIndicator(targetFraction = selectedTabIndex.toFloat(), height = height)
}

@Composable
private fun TabIndicatorScope.SpringIndicator(
    targetFraction: Float,
    height: Dp
) {
    // Tab positions arrive at measure time only; capture them so composition
    // can compute animation targets. The equality guard keeps measure passes
    // from scheduling recompositions once positions stabilize.
    var tabPositions by remember { mutableStateOf<List<TabPosition>>(emptyList()) }
    val density = LocalDensity.current

    val leftAnim = remember { Animatable(0f) }
    val rightAnim = remember { Animatable(0f) }

    if (tabPositions.isNotEmpty()) {
        val clampedFraction = targetFraction.coerceIn(0f, (tabPositions.size - 1).toFloat())
        val leftIndex = clampedFraction.toInt().coerceIn(0, tabPositions.size - 1)
        val rightIndex = (leftIndex + 1).coerceAtMost(tabPositions.size - 1)
        val ratio = clampedFraction - leftIndex

        val leftPos = tabPositions[leftIndex]
        val rightPos = tabPositions[rightIndex]

        val targetLeftPx = with(density) {
            (leftPos.left.toPx() + (rightPos.left.toPx() - leftPos.left.toPx()) * ratio)
                .coerceAtLeast(0f)
        }
        val targetRightPx = with(density) {
            (leftPos.right.toPx() + (rightPos.right.toPx() - leftPos.right.toPx()) * ratio)
                .coerceAtMost(tabPositions.last().right.toPx())
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
    }

    Box(
        modifier = Modifier
            .tabIndicatorLayout { measurable, constraints, positions ->
                if (positions.isNotEmpty() && positions != tabPositions) {
                    tabPositions = positions
                }
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = with(density) { leftAnim.value.toDp() })
                .width(with(density) { (rightAnim.value - leftAnim.value).toDp() })
                .height(height)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall
                )
        )
    }
}
