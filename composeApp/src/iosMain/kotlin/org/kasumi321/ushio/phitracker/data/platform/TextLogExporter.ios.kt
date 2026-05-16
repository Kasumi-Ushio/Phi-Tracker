@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun shareTextLog(text: String, fileName: String): Result<Unit> {
    val tempPath = NSTemporaryDirectory() + fileName
    val tempUrl = NSURL.fileURLWithPath(tempPath)
    return try {
        val data = text.encodeToByteArray().toNSData()
        check(data.writeToURL(tempUrl, atomically = true)) {
            "Failed to write log to temporary file"
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

private suspend fun presentShareSheet(fileUrl: NSURL, tempPath: String) {
    suspendCancellableCoroutine<Unit> { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController == null) {
                removeTemporaryFile(tempPath)
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
        val errorPtr = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        NSFileManager.defaultManager.removeItemAtPath(path, errorPtr.ptr)
    }
}
