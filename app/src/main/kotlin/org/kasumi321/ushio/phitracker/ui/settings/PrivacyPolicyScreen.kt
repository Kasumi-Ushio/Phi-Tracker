package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私政策") },
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
                text = "Phi Tracker 隐私政策",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            PolicyText("最后更新日期：2026 年 4 月 3 日")

            Spacer(modifier = Modifier.height(16.dp))
            PolicyText("感谢您使用 Phi Tracker（以下简称“本应用”）。我们非常重视您的隐私，并深知您的信赖对我们非常重要，因此我们会按照法律要求以及业界成熟的安全标准，通过合理有效的方式保护您的个人信息。本隐私政策旨在向您说明本应用如何收集、使用和保护您的数据。")
            PolicyText("我们将通过本隐私政策帮助您了解我们在收集、使用、存储、共享和保护您的个人信息方面所做的努力。")
            PolicyText("如您对本隐私政策的内容存在疑问，请您按照“九、联系我们”中的方式与我们取得联系，我们将尽快回复您；如您不同意本隐私政策的内容，请您停止使用本应用。")

            PolicyTitle("一、我们收集的信息")
            PolicyText("本应用在运行过程中可能涉及以下数据：")
            PolicyText("1. TapTap 账号凭证：当您选择通过 TapTap 扫码登录时，本应用会获取 TapTap 授权的 sessionToken，用于从 Phigros 云端服务器读取您的游戏存档数据。")
            PolicyText("2. Phigros 游戏存档：包括您的游戏昵称、课题模式评级、游玩记录（成绩、准确率、Full Combo 状态等）和 RKS 数值。")
            PolicyText("3. 本地设置偏好：包括您选择的主题模式、自定义头像的本地 URI、B30 溢出显示设置等。")
            PolicyText("4. 运行日志（仅 Debug 版本）：Debug 版本提供导出运行日志功能。该日志用于排查故障，可能包含网络请求相关技术信息（在特定情况下可能出现账号凭证字段，如 sessionToken）。请仅在您信任的渠道中分享日志，并在公开前自行检查和脱敏。")
            PolicyText("5. Phi-Plugin「滦鸠」联合查分 API 请求凭证（可选）：当您选择在设置页面中开启“启用查分 API”功能时，本应用将根据您填写的“平台名称”和“平台 ID”向该 API 发送请求，以获取对应的查分数据。")

            PolicyTitle("二、数据的使用方式")
            PolicyText("您的数据仅用于以下目的：")
            PolicyText("• 读取并展示您的 Phigros 游戏存档与成绩分析（B30、RKS 计算等）。")
            PolicyText("• 存储您的个性化设置，以便在下次启动时恢复。")
            PolicyText("• 缓存曲绘资源，减少网络流量消耗。")
            PolicyText("• 根据您开启查分 API 功能后填写的平台名称和平台 ID，向该 API 发送请求，以获取对应的查分数据。")

            PolicyTitle("三、数据的存储与安全")
            PolicyText("所有数据均存储在您的设备本地。本应用不会将您的数据上传至任何我们所控制的远程服务器。")
            PolicyText("具体而言：")
            PolicyText("• 游戏存档和成绩数据存储在应用本地的 Room 数据库中。")
            PolicyText("• 设置偏好存储在 Android SharedPreferences 中。")
            PolicyText("• 曲绘缓存存储在应用的专用缓存目录中。")
            PolicyText("• sessionToken 存储在应用的本地数据库中，且不会以任何形式被传输到除 TapTap 以外的第三方。")
            PolicyText("但请您理解，互联网并非绝对安全的环境，任何安全措施都无法做到无懈可击。我们建议您采取积极措施保护个人信息的安全。")

            PolicyTitle("四、第三方服务")
            PolicyText("本应用在运行过程中会与以下第三方服务进行交互：")
            PolicyText("1. TapTap OAuth 服务：用于实现扫码登录功能，获取授权凭证。交互过程遵循 TapTap 的隐私政策。")
            PolicyText("2. Phigros 云端存档服务（LeanCloud）：用于读取您的游戏存档数据。")
            PolicyText("3. GitHub（raw.githubusercontent.com）：用于获取曲绘图片资源和曲目数据更新。")
            PolicyText("4. gh-proxy（gh-proxy.com）：用于在中国内地环境中加速请求和获取 GitHub 资源。")
            PolicyText("5. Phi-Plugin「滦鸠」联合查分 API（可选）：用于获取排行榜、RKS 历史记录等数据。")
            PolicyText("本应用不使用任何第三方分析、广告或追踪 SDK。")

            PolicyTitle("五、您的权利")
            PolicyText("您可以随时：")
            PolicyText("• 退出登录，清除本地存储的账号凭证。")
            PolicyText("• 在设置中清理本地缓存的曲绘资源。")
            PolicyText("• 通过 Android 系统的\"应用信息\"功能清除本应用的全部本地数据。")
            PolicyText("• 卸载本应用以完全删除所有相关数据。")

            PolicyTitle("六、儿童隐私")
            PolicyText("我们非常重视未成年人的个人信息保护工作。根据相关法律法规的规定，若您是 18 周岁以下的未成年人，在使用我们的服务前，应在您监护人的监护、指导下共同阅读并同意本隐私政策。")
            PolicyText("我们只会在法律允许、未成年人的监护人明确同意或保护未成年人所必要的情况下获取、存储、使用、共享、转让、披露未成年人的个人信息。若您是未成年人的监护人，当您对您所监护的未成年人的个人信息有相关疑问时，请通过本隐私政策公示的联系方式与我们联系。")

            PolicyTitle("七、隐私政策的更新")
            PolicyText("我们可能会不时更新本隐私政策。更新后的隐私政策将随应用版本更新发布。我们建议您定期查阅本隐私政策以了解最新信息。")

            PolicyTitle("八、第三方服务隐私政策")
            PolicyText("我们在此列举我们所使用的第三方服务的隐私政策，您可以点击下方的链接以查看：")
            PolicyText("1. TapTap OAuth 服务 & LeanCloud：https://www.taptap.cn/doc/privacy-policy/")
            PolicyText("2. Phigros：https://phigros.pigeongames.cn/privacy_policy.txt")
            PolicyText("3. GitHub：https://docs.github.com/zh/site-policy/privacy-policies/github-general-privacy-statement")
            PolicyText("部分第三方服务目前暂没有提供隐私政策文本，我们将在这些第三方服务提供隐私政策文本后及时更新本隐私政策。")

            PolicyTitle("九、联系我们")
            PolicyText("如果您对本隐私政策有任何疑问或建议，请通过以下方式与我们联系：")
            PolicyText("• GitHub Issues：https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros/issues")

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicyTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PolicyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
