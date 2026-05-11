@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
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
import platform.Foundation.dataWithContentsOfURL
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
        require(imageUrl.contains("/ill/") && !imageUrl.contains("/illLow/")) {
            "Low-res artwork save is not supported. Expected standard URL containing /ill/."
        }
        val nsData = downloadArtworkData(imageUrl)

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

private suspend fun downloadArtworkData(imageUrl: String): NSData = withContext(Dispatchers.Default) {
    val url = requireNotNull(NSURL.URLWithString(imageUrl)) { "Invalid artwork URL" }
    val data = requireNotNull(NSData.dataWithContentsOfURL(url)) { "Unable to download artwork" }
    require(data.length > 0u) { "Downloaded artwork is empty" }

    data
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
