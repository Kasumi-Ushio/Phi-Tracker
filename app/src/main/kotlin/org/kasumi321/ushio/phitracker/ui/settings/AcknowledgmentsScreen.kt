package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgmentsScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("致谢与参考项目") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "特别致谢",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionText("感谢 Pigeon Games (鸽游网络) 持续为玩家带来 Phigros 这样一款优秀的音乐游戏。")
            SectionText("感谢 TapTap 提供的云服务和开发者工具，让这个工具的实现成为可能。")
            

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "参考项目版权与许可",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            SectionTitle("Catrong/phi-plugin")
            SectionText("提供 Phigros 谱面定数等关键元数据的开源数据仓库，存档解析、分类等功能的灵感与参考来源。")
            SectionText("版权所有 (c) 2026 Catrong 及 phi-plugin 贡献者。\nSPDX-License-Identifier: GPL-3.0-only。")

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle("7aGiven/PhigrosLibrary")
            SectionText("用于解析 Phigros 本地与云端存档数据的核心解析器来源，本项目 SaveParser 基于其 C 语言版本移植开发。")
            SectionText("版权所有 (c) 2026 7aGiven 及 PhigrosLibrary 贡献者。\nSPDX-License-Identifier: GPL-3.0-only。")
            SectionText(
                buildAnnotatedString {
                    append("我们已获得原作者")
                    withLink(LinkAnnotation.Url("https://github.com/7aGiven/PhigrosLibrary/issues/11")) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append("授权")
                        }
                    }
                    append("，对于本项目：SPDX-License-Identifier: GPL-3.0-or-later。")
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "谨在此向以上参考项目的作者和所有自由开源社区的贡献者们表达最诚挚的敬意。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "最后的最后",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "最后，我们诚挚和衷心的感谢所有 Phigros 玩家和所有为 Phigros 社区做出贡献的人们。\n是你们对这款游戏的热爱，才有了今天这个充满活力的社区，也才有了 Phi Tracker 的诞生。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SectionText(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
