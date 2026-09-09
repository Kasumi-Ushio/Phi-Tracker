package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import org.kasumi321.ushio.phitracker.ui.glass.ExpandableGlassSection
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

/**
 * Unified songs glass header built from a single layout tree: the title, marquee
 * tip and search field stay composed in both states and every property that
 * differs between expanded and compact is interpolated per frame — title font
 * size, the search field's height/alpha (via [ExpandableGlassSection]) and the
 * compact action icons sliding in from the end — instead of crossfading between
 * two unrelated layouts.
 *
 * The filter entry lives inside the search field's trailing slot so the
 * placeholder gets the full row width; its text renders one type-scale step
 * smaller and ellipsizes rather than wrapping on narrow screens. Tapping the
 * compact search icon asks the caller to reopen the field with focus and IME
 * ([focusOnExpand]); automatic re-expansion at the top of the list does not.
 * With reduced motion enabled every part jumps straight to the final state.
 */
@Composable
fun SongsHeader(
    songCount: Int,
    tip: String,
    searchQuery: String,
    activeFilterCount: Int,
    compact: Boolean,
    focusOnExpand: Boolean,
    onExpandFocusHandled: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchExpandRequest: () -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val focusRequester = remember { FocusRequester() }
    var wasCompact by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(compact) {
        if (compact) {
            // A collapse before the expansion animation settles must not leave
            // a stale focus request armed
            onExpandFocusHandled()
        } else if (wasCompact == true && focusOnExpand) {
            focusRequester.requestFocus()
            onExpandFocusHandled()
        }
        wasCompact = compact
    }

    val expansion by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "songsHeaderExpansion"
    )
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleFontSize = lerp(16f, 22f, expansion)
    val titleLineHeight = lerp(24f, 28f, expansion)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title slot mirrors the other tabs' headers: the tip hugs the
            // title instead of sitting below the action row
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "全部曲目 ($songCount)",
                    style = titleStyle,
                    fontSize = titleFontSize.sp,
                    lineHeight = titleLineHeight.sp
                )
                ExpandableGlassSection(expanded = tip.isNotBlank()) {
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                }
            }
            AnimatedVisibility(
                visible = compact,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            expandFrom = Alignment.End
                        )
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                        shrinkHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            shrinkTowards = Alignment.End
                        )
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearchExpandRequest) {
                        Icon(Icons.Filled.Search, contentDescription = "展开搜索")
                    }
                    SongsFilterEntry(
                        activeFilterCount = activeFilterCount,
                        onOpenFilter = onOpenFilter
                    )
                }
            }
        }

        ExpandableGlassSection(expanded = !compact) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            "搜索曲名、作曲或别名...",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                                }
                            }
                            SongsFilterEntry(
                                activeFilterCount = activeFilterCount,
                                onOpenFilter = onOpenFilter
                            )
                        }
                    },
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(lerp(4f, 12f, expansion).dp))
    }
}

/**
 * Badged filter icon shared by the compact title row and the search field's
 * trailing slot.
 */
@Composable
private fun SongsFilterEntry(
    activeFilterCount: Int,
    onOpenFilter: () -> Unit
) {
    IconButton(onClick = onOpenFilter) {
        BadgedBox(
            badge = {
                if (activeFilterCount > 0) {
                    Badge { Text(activeFilterCount.toString()) }
                }
            }
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = "Filter",
                tint = if (activeFilterCount > 0)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
