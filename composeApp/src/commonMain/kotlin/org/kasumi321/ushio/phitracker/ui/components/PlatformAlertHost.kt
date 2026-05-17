package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.kasumi321.ushio.phitracker.data.platform.PlatformAlertController

@Composable
fun PlatformAlertHost() {
    val alert by PlatformAlertController.alert.collectAsState()
    val content = alert ?: return

    AlertDialog(
        onDismissRequest = { PlatformAlertController.dismiss(content.id) },
        title = { Text(content.title) },
        text = { Text(content.message) },
        confirmButton = {
            TextButton(onClick = { PlatformAlertController.dismiss(content.id) }) {
                Text("确定")
            }
        }
    )
}
