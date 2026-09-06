package org.kasumi321.ushio.phitracker.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
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
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("最新版本: $version")
                if (body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // The release body is GitHub-flavored Markdown and can be
                    // longer than the dialog: bound the height and let users
                    // scroll through the whole note. GitHub release bodies use
                    // CRLF line endings, which the markdown parser mishandles
                    // (paragraph breaks collapse), so normalize them first.
                    val normalizedBody = remember(body) { body.replace("\r\n", "\n").replace('\r', '\n') }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // The m3 wrapper supplies MaterialTheme-derived
                        // defaults for every style; only the heading scale is
                        // overridden to keep the release note readable inside
                        // a bounded dialog.
                        Markdown(
                            content = normalizedBody,
                            modifier = Modifier.fillMaxWidth(),
                            colors = markdownColor(
                                text = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            typography = markdownTypography(
                                h1 = MaterialTheme.typography.titleLarge,
                                h2 = MaterialTheme.typography.titleMedium,
                                h3 = MaterialTheme.typography.titleSmall,
                                h4 = MaterialTheme.typography.titleSmall,
                                h5 = MaterialTheme.typography.titleSmall,
                                h6 = MaterialTheme.typography.titleSmall
                            )
                        )
                    }
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
