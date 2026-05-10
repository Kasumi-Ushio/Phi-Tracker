@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary

actual suspend fun saveArtworkToPictures(imageUrl: String, fileName: String): Result<Unit> {
    val tempDir = NSTemporaryDirectory()
    val tempPath = tempDir + fileName
    val tempUrl = NSURL.fileURLWithPath(tempPath)

    return try {
        val bytes = loadArtworkBytes(imageUrl)
        val nsData = bytes.toNSData()

        check(nsData.writeToURL(tempUrl, atomically = true)) {
            "Failed to write artwork to temporary file"
        }

        authorizePhotosAddOnly()

        saveImageToPhotos(tempUrl)

        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    } finally {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            NSFileManager.defaultManager.removeItemAtPath(tempPath, errorPtr.ptr)
        }
    }
}

actual fun showPlatformMessage(message: String) = Unit

private suspend fun loadArtworkBytes(imageUrl: String): ByteArray = withContext(Dispatchers.Default) {
    val context = PlatformContext.INSTANCE
    val imageLoader = SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .diskCacheKey(imageUrl)
        .build()
    val result = imageLoader.execute(request)
    require(result is SuccessResult) { "Unable to load artwork" }

    val diskCache = requireNotNull(imageLoader.diskCache) { "Coil disk cache is unavailable" }
    val diskCacheKey = result.diskCacheKey ?: imageUrl
    val snapshot = requireNotNull(diskCache.openSnapshot(diskCacheKey)) { "Artwork cache entry is unavailable" }
    snapshot.use {
        diskCache.fileSystem.read(snapshot.data) {
            readByteArray()
        }
    }
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(
        bytes = if (isEmpty()) null else pinned.addressOf(0),
        length = size.toULong()
    )
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun authorizePhotosAddOnly() {
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
    when {
        status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited -> return
        status == PHAuthorizationStatusDenied || status == PHAuthorizationStatusRestricted ->
            error("Photo library access is denied. Enable it in Settings > Privacy > Photos.")
        else -> {
            val newStatus = suspendCancellableCoroutine { cont ->
                PHPhotoLibrary.requestAuthorizationForAccessLevel(
                    PHAccessLevelAddOnly
                ) { result ->
                    cont.resume(result)
                }
            }
            when {
                newStatus == PHAuthorizationStatusAuthorized || newStatus == PHAuthorizationStatusLimited -> return
                else -> error("Photo library access was not granted")
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun saveImageToPhotos(fileUrl: NSURL) {
    suspendCancellableCoroutine<Unit> { cont ->
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            {
                PHAssetChangeRequest.creationRequestForAssetFromImageAtFileURL(fileUrl)
            },
            completionHandler = { success, error ->
                if (success) {
                    cont.resume(Unit)
                } else {
                    val message = error?.localizedDescription ?: "Unknown error"
                    cont.resumeWithException(RuntimeException("Failed to save to Photos: $message"))
                }
            }
        )
    }
}
