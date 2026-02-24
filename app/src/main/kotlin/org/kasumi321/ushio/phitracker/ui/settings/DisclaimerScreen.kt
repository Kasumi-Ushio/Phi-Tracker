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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("版权、许可协议与免责声明") },
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
                text = "版权与许可协议",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("版权")
            SectionText("版权所有 (c) 2026 Kasumi's IT Infrastructure。\n\n版权所有 (c) 2026 铃萤-RinLin a.k.a. 朝比奈ほたる 及所有 Phi Tracker 贡献者。")

            SectionTitle("本项目许可协议")
            SectionText("本项目的全部源代码、不属于 Pigeon Games 和 TapTap 的资产以及文档，均为自由软件。\n\n您可以根据 GNU General Public License 版本 3 的条款，使用、复制、分发、修改和重新分发本项目。")
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "免责声明",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionText("本项目是一个非官方项目，与 Pigeon Games 和 TapTap 之间没有任何关联，也不以任何形式受其认可或支持。用户在使用过程中导入的所有资源均为其各自所有者的财产，我们不主张也不拥有这些资源的任何权利。")
            
            SectionText("尽管本项目是自由软件，并依据 GNU 通用公共许可证的版本 3 允许商业使用，但我们不鼓励、不支持任何形式的商业用途。任何因商业用途而产生的反馈、问题或争议，我们概不负责。")

            Text(
                text = "我们不支持，且法律明确禁止任何形式的对 TapTap 和/或 Pigeon Games 的网络攻击或其他非法行为。使用本项目进行此类活动所产生的任何法律风险由用户自行承担。绝对禁止将本项目用于在用户所在国家或地区的刑法所禁止的任何形式的非法活动。",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "本项目不提供任何形式的担保，包括但不限于适销性、特定用途的适用性和非侵权性。在任何情况下，我们均不对因使用本项目而产生的任何索赔、损害或其他责任负责。",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
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
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}
