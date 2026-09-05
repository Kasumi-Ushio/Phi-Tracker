package org.kasumi321.ushio.phitracker.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog

@Composable
fun UpdateResultDialog(
    version: String,
    body: String,
    htmlUrl: String,
    onDismiss: () -> Unit,
    onDownload: (UriHandler) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("最新版本: $version")
                if (body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(uriHandler) }) { Text("前往下载") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后再说") }
        }
    )
}
