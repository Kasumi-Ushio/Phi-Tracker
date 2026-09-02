package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.ui.glass.ExpandableGlassSection
import org.kasumi321.ushio.phitracker.ui.glass.rememberExpansionArrowRotation

/**
 * Collapsible B30 glass header. Replaces the former top bar plus standalone RKS
 * card with one continuous glass surface. The tip line hugs the title and stays
 * visible in both states, like the other tabs' top bars. Collapsed the header
 * keeps the page identity, the current RKS and the image generation entry;
 * expanded it also shows Best Phi and the B27 tail, preceded by a reserved gap
 * where the tip used to sit so the RKS row keeps its distance from the title.
 * The whole header toggles expansion, the arrow hit target is not limited to
 * the icon itself.
 */
@Composable
fun B30Header(
    state: B30UiState,
    tip: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onGenerateImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrowRotation by rememberExpansionArrowRotation(expanded)
    val phi3 = state.b30.filter { it.isPhi }
    val b27 = state.b30.filter { !it.isPhi }.take(27)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clickable(
                onClickLabel = if (expanded) "收起 RKS 信息" else "展开 RKS 信息",
                role = Role.Button,
                onClick = onToggle
            )
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Best 30",
                style = MaterialTheme.typography.titleLarge
            )

            // Collapsed keeps the most critical info inline: current RKS
            androidx.compose.animation.AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "RKS ${state.displayRks.formatFour()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起 RKS 信息" else "展开 RKS 信息",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation)
            )

            IconButton(
                onClick = onGenerateImage,
                enabled = state.b30.isNotEmpty()
            ) {
                Icon(Icons.Filled.Image, contentDescription = "生成图片")
            }
        }

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

        ExpandableGlassSection(expanded = expanded) {
            Column {
                // Blank gap where the tip line used to sit, keeping the RKS row
                // at a reasonable distance from the title
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RKS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.displayRks.formatFour(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.b30.isNotEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            if (phi3.isNotEmpty()) {
                                Text(
                                    text = "Best φ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = phi3.first().rks.formatFour(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (b27.size >= 27) {
                                Text(
                                    text = "B27 末位: ${b27.last().rks.formatFour()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
