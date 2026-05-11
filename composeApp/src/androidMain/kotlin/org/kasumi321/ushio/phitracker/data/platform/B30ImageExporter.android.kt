package org.kasumi321.ushio.phitracker.data.platform

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun saveB30ImageToPictures(pngBytes: ByteArray, fileName: String): Result<Unit> = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeB30ImageToMediaStore(pngBytes, fileName)
    } else {
        SafTreeManager.getInstance().savePngBytes(pngBytes, fileName).getOrThrow()
    }
}

actual suspend fun shareB30Image(pngBytes: ByteArray, fileName: String): Result<Unit> = runCatching {
    val context = requireNotNull(AndroidPlatformContext.applicationContext) { "Android context is not initialized" }
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeB30ImageToMediaStore(pngBytes, fileName)
    } else {
        SafTreeManager.getInstance().savePngBytes(pngBytes, fileName).getOrThrow()
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享 B30").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun writeB30ImageToMediaStore(pngBytes: ByteArray, fileName: String): Uri = withContext(Dispatchers.IO) {
    val context = requireNotNull(AndroidPlatformContext.applicationContext) { "Android context is not initialized" }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhiTracker")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = requireNotNull(
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    ) { "Unable to create media entry" }

    try {
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open media output" }
            output.write(pngBytes)
        }
        val completeValues = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, completeValues, null, null)
        uri
    } catch (e: Throwable) {
        context.contentResolver.delete(uri, null, null)
        throw e
    }
}
