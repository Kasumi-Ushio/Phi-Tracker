package org.kasumi321.ushio.phitracker.ui.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.ui.glass.ExpandableGlassSection

/**
 * Unified songs glass header: title, tip, search field and filter entry share
 * one progressive glass surface. Scrolling the list collapses the header to a
 * single row holding the tightened title plus a search icon on the top right;
 * tapping the icon asks the caller to expand the header again, restoring the
 * full search field. Height never reacts to focus or the keyboard, so the
 * query, clear button and filter badge remain usable in every state.
 */
@Composable
fun SongsHeader(
    songCount: Int,
    tip: String,
    searchQuery: String,
    activeFilterCount: Int,
    compact: Boolean,
    onSearchChange: (String) -> Unit,
    onSearchExpandRequest: () -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部曲目 ($songCount)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
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
                    modifier = Modifier.weight(1f),
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
