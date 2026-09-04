package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.ui.glass.ExpandableGlassSection
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

/**
 * Unified songs glass header: title, tip, search field and filter entry share
 * one progressive glass surface. Scrolling the list crossfades the header into
 * a compact block: the search field shrinks and fades out while a search icon
 * fades in on the top right of a slim title row, with the marquee tip kept
 * below it. Tapping the icon asks the caller to reopen the search field with
 * an expansion animation, and any further scroll collapses it again. Only an
 * icon-triggered reopening ([focusOnExpand]) focuses the field and raises the
 * IME; automatic re-expansion when the list scrolls back to the top does not.
 * With reduced motion enabled the swap jumps straight to the final state.
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        AnimatedContent(
            targetState = compact,
            transitionSpec = {
                if (reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = tween(250)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
                        )
                        .using(SizeTransform(clip = true))
                }
            },
            label = "songsHeaderCollapse"
        ) { isCompact ->
            if (isCompact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title slot mirrors the other tabs' headers: the tip hugs
                    // the title instead of sitting below the 48dp icon row
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "全部曲目 ($songCount)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (tip.isNotBlank()) {
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
                    IconButton(onClick = onSearchExpandRequest) {
                        Icon(Icons.Filled.Search, contentDescription = "展开搜索")
                    }
                    SongsFilterEntry(
                        activeFilterCount = activeFilterCount,
                        onOpenFilter = onOpenFilter,
                        compact = true
                    )
                }
            } else {
                Column {
                    Text(
                        text = "全部曲目 ($songCount)",
                        style = MaterialTheme.typography.titleLarge
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text("搜索曲名或作曲...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                                    }
                                }
                            },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SongsFilterEntry(
                            activeFilterCount = activeFilterCount,
                            onOpenFilter = onOpenFilter,
                            compact = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))
    }
}

/**
 * Filter entry shared by both header states. The expanded state keeps the
 * boxed button aligned with the search field; the compact state falls back to
 * a plain icon so the collapsed row stays slim.
 */
@Composable
private fun SongsFilterEntry(
    activeFilterCount: Int,
    onOpenFilter: () -> Unit,
    compact: Boolean
) {
    if (compact) {
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
    } else {
        IconButton(
            onClick = onOpenFilter,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (activeFilterCount > 0)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            if (activeFilterCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(activeFilterCount.toString()) }
                    }
                ) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filter",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
