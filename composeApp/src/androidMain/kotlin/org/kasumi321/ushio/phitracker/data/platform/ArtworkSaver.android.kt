package org.kasumi321.ushio.phitracker.data.platform

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun saveArtworkToPictures(imageUrl: String, fileName: String): Result<Unit> = runCatching {
    val context = requireNotNull(AndroidPlatformContext.applicationContext) { "Android context is not initialized" }
    val bitmap = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        require(result is SuccessResult) { "Unable to load artwork" }
        result.image.toBitmap()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        withContext(Dispatchers.IO) {
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
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to write artwork" }
                }
                val completeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, completeValues, null, null)
            } catch (e: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        }
    } else {
        SafTreeManager.getInstance().saveBitmap(bitmap, fileName).getOrThrow()
    }
}

actual fun showPlatformMessage(message: String) {
    val context = AndroidPlatformContext.applicationContext ?: return
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
