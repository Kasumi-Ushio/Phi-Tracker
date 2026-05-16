package org.kasumi321.ushio.phitracker.data.platform

import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.data.logging.LogRedactor
import java.io.File

actual suspend fun shareTextLog(text: String, fileName: String): Result<Unit> = runCatching {
    val context = requireNotNull(AndroidPlatformContext.applicationContext) {
        "Android context is not initialized"
    }
    val redacted = LogRedactor.redact(text)
    val uri = writeTextToFile(redacted, fileName)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun writeTextToFile(text: String, fileName: String): android.net.Uri =
    withContext(Dispatchers.IO) {
        val context = requireNotNull(AndroidPlatformContext.applicationContext) {
            "Android context is not initialized"
        }
        val logDir = File(context.cacheDir, "log_export")
        if (!logDir.exists()) logDir.mkdirs()
        val file = File(logDir, fileName)
        file.writeText(text, Charsets.UTF_8)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
