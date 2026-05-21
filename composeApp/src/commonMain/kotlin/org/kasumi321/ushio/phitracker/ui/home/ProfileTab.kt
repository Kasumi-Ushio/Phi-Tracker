package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.kasumi321.ushio.phitracker.data.platform.rememberAvatarPicker
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors

private val ChallengeTierColors = listOf(
    Color(0xFFCCCCCC),
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFFF44336),
    Color(0xFFFFD700),
    Color.Unspecified
)

private val RainbowBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFF0000),
        Color(0xFFFF7F00),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF0000FF),
        Color(0xFF4B0082),
        Color(0xFF9400D3)
    )
)

private val FcColor = Color(0xFF4FC3F7)
private val PhiColor = Color(0xFFFFD54F)
private val PhiTextColor = Color(0xFF5D4037)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileTab(
    nickname: String,
    displayRks: Float,
    challengeModeRank: Int,
    moneyString: String,
    clearCounts: Map<String, Int>,
    fcCount: Int,
    phiCount: Int,
    avatarUri: String?,
    lastSyncTime: Long?,
    recentSyncedRecords: List<BestRecord>,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onAvatarSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onSongClick: (String, Difficulty?) -> Unit,
    getIllustrationUrl: (String) -> String?,
    tip: String = "",
    modifier: Modifier = Modifier
) {
    val launchPicker = rememberAvatarPicker { uri ->
        uri?.let { onAvatarSelected(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("首页")
                    if (tip.isNotBlank()) {
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth(1f)
                                .basicMarquee()
                        )
                    }
                }
            },
            actions = {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "同步")
                    }
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            ProfileHeaderCard(
                nickname = nickname,
                displayRks = displayRks,
                challengeModeRank = challengeModeRank,
                moneyString = moneyString,
                avatarUri = avatarUri,
                onAvatarClick = { launchPicker() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatsTableCard(
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "最近同步",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (lastSyncTime != null) {
                val formattedTime = epochMillisToDateTimeString(lastSyncTime)
                Text(
                    text = "同步时间: $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (recentSyncedRecords.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSyncedRecords.forEachIndexed { index, record ->
                            ScoreCard(
                                rank = index + 1,
                                record = record,
                                illustrationUrl = getIllustrationUrl(record.songId),
                                onSongClick = onSongClick
                            )
                        }
                    }
                } else {
                    Text(
                        text = "本次同步没有检测到分数或 ACC 变化",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        text = "尚未同步过存档\n点击右上角刷新按钮开始同步",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ProfileHeaderCard(
    nickname: String,
    displayRks: Float,
    challengeModeRank: Int,
    moneyString: String,
    avatarUri: String?,
    onAvatarClick: (() -> Unit)? = null,
    contentHorizontalPadding: Dp = 20.dp,
    contentVerticalPadding: Dp = 20.dp,
    textVerticalSpacing: Dp = 3.dp,
    avatarSize: Dp = 72.dp,
    avatarTextSpacing: Dp = 16.dp,
    centerContent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centerContent) Arrangement.spacedBy(avatarTextSpacing) else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (onAvatarClick != null) Modifier.clickable { onAvatarClick() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    val imageRequest = remember(platformContext, avatarUri) {
                        ImageRequest.Builder(platformContext)
                            .data(avatarUri)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "设置头像",
                        modifier = Modifier.size(avatarSize * 0.4f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(avatarTextSpacing))

            Column(
                verticalArrangement = Arrangement.spacedBy(textVerticalSpacing),
                horizontalAlignment = if (centerContent) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Text(
                    text = nickname.ifBlank { "未登录" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (centerContent) TextAlign.Center else TextAlign.Start
                )
                if (moneyString.isNotBlank()) {
                    Text(
                        text = "Data: $moneyString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (centerContent) TextAlign.Center else TextAlign.Start
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayRks.formatFour(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (challengeModeRank > 0) {
                        ChallengeBadge(challengeModeRank)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsTableCard(
    clearCounts: Map<String, Int>,
    fcCount: Int,
    phiCount: Int,
    contentHorizontalPadding: Dp = 16.dp,
    contentVerticalPadding: Dp = 16.dp,
    rowSpacing: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DifficultyStatItem("EZ", clearCounts["EZ"] ?: 0, DifficultyColors.EZ)
                DifficultyStatItem("HD", clearCounts["HD"] ?: 0, DifficultyColors.HD)
                DifficultyStatItem("IN", clearCounts["IN"] ?: 0, DifficultyColors.IN)
                DifficultyStatItem("AT", clearCounts["AT"] ?: 0, DifficultyColors.AT)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BadgeStatItem("FC", fcCount, FcColor, Color.White)
                BadgeStatItem("\u03C6", phiCount, PhiColor, PhiTextColor)
            }
        }
    }
}

@Composable
private fun DifficultyStatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BadgeStatItem(label: String, count: Int, bgColor: Color, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 13.sp
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChallengeBadge(challengeModeRank: Int) {
    val tier = challengeModeRank / 100
    val level = challengeModeRank % 100

    val isRainbow = tier == 5
    val bgColor = if (!isRainbow && tier in ChallengeTierColors.indices) {
        ChallengeTierColors[tier]
    } else {
        Color.Transparent
    }
    val textColor = when (tier) {
        0 -> Color(0xFF333333)
        4 -> Color(0xFF5D4037)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isRainbow) {
                    Modifier.background(RainbowBrush)
                } else {
                    Modifier.background(bgColor)
                }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$level",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isRainbow) Color.White else textColor,
            fontSize = 13.sp
        )
    }
}
