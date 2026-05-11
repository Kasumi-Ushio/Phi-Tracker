@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
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
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun saveB30ImageToPictures(pngBytes: ByteArray, fileName: String): Result<Unit> {
    return try {
        val image = requireNotNull(UIImage(data = pngBytes.toNSData())) { "Unable to decode B30 PNG" }
        authorizePhotosAddOnly()
        saveImageToPhotos(image)
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

actual suspend fun shareB30Image(pngBytes: ByteArray, fileName: String): Result<Unit> {
    val tempPath = NSTemporaryDirectory() + fileName
    val tempUrl = NSURL.fileURLWithPath(tempPath)
    return try {
        check(pngBytes.toNSData().writeToURL(tempUrl, atomically = true)) {
            "Failed to write B30 image to temporary file"
        }
        presentShareSheet(tempUrl, tempPath)
        Result.success(Unit)
    } catch (e: Throwable) {
        removeTemporaryFile(tempPath)
        Result.failure(e)
    }
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(
        bytes = if (isEmpty()) null else pinned.addressOf(0),
        length = size.toULong()
    )
}

private suspend fun authorizePhotosAddOnly() {
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
    when {
        status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited -> return
        status == PHAuthorizationStatusDenied || status == PHAuthorizationStatusRestricted ->
            error("Photo library access is denied. Enable it in Settings > Privacy > Photos.")
        else -> {
            val newStatus = suspendCancellableCoroutine { cont ->
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { result ->
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

private suspend fun saveImageToPhotos(image: UIImage) {
    suspendCancellableCoroutine<Unit> { cont ->
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            {
                PHAssetChangeRequest.creationRequestForAssetFromImage(image)
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

private suspend fun presentShareSheet(fileUrl: NSURL, tempPath: String) {
    suspendCancellableCoroutine<Unit> { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController == null) {
                cont.resumeWithException(IllegalStateException("Unable to find root view controller"))
                return@dispatch_async
            }
            val activityController = UIActivityViewController(listOf(fileUrl), null)
            activityController.completionWithItemsHandler = { _, _, _, _ ->
                removeTemporaryFile(tempPath)
            }
            cont.invokeOnCancellation { removeTemporaryFile(tempPath) }
            rootViewController.presentViewController(activityController, animated = true) {
                cont.resume(Unit)
            }
        }
    }
}

private fun removeTemporaryFile(path: String) {
    memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        NSFileManager.defaultManager.removeItemAtPath(path, errorPtr.ptr)
    }
}
