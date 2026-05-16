@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Clock

@Composable
actual fun rememberAvatarPicker(onResult: (String?) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val delegate = remember { AvatarPickerDelegate { currentOnResult(it) } }

    return {
        dispatch_async(dispatch_get_main_queue()) {
            delegate.launchPicker()
        }
    }
}

private class AvatarPickerDelegate(
    private val onResult: (String?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    private var picker: UIImagePickerController? = null
    private var strongRef: AvatarPickerDelegate? = null

    fun launchPicker() {
        val presenter = topMostViewController()
            ?: run {
                onResult(null)
                return
            }

        val imagePicker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceTypePhotoLibrary
            allowsEditing = true
            delegate = this@AvatarPickerDelegate
        }
        picker = imagePicker
        strongRef = this
        presenter.presentViewController(imagePicker, animated = true, null)
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val editedImage = didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage] as? UIImage
        val originalImage = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val image = editedImage ?: originalImage

        picker.dismissViewControllerAnimated(true, null)
        this.picker = null
        strongRef = null

        if (image == null) {
            onResult(null)
            return
        }

        val savedPath = saveAvatarImage(image)
        onResult(savedPath)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        this.picker = null
        strongRef = null
        onResult(null)
    }

    private fun saveAvatarImage(image: UIImage): String? {
        return runCatching {
            val paths = createPlatformPaths()
            val avatarDir = "${paths.filesDir}/avatar"

            val fm = NSFileManager.defaultManager
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                if (!fm.fileExistsAtPath(avatarDir)) {
                    fm.createDirectoryAtPath(
                        path = avatarDir,
                        withIntermediateDirectories = true,
                        attributes = null,
                        error = err.ptr
                    )
                }
            }

            val timestamp = Clock.System.now().toEpochMilliseconds()
            val fileName = "avatar_$timestamp.png"
            val filePath = "$avatarDir/$fileName"
            val fileUrl = NSURL.fileURLWithPath(filePath)

            val pngData = UIImagePNGRepresentation(image)
                ?: throw RuntimeException("Failed to convert avatar image to PNG")

            check(pngData.writeToURL(fileUrl, atomically = true)) {
                "Failed to write avatar image to file"
            }

            cleanupOldAvatars(avatarDir, keepCount = 3)

            filePath
        }.getOrNull()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cleanupOldAvatars(avatarDir: String, keepCount: Int) {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(avatarDir, null)
            ?.filterIsInstance<String>()
            ?.filter { it.startsWith("avatar_") && it.endsWith(".png") }
            ?: return

        if (contents.size <= keepCount) return

        val sorted = contents.sortedByDescending { name ->
            name.removePrefix("avatar_").removeSuffix(".png").toLongOrNull() ?: 0L
        }

        sorted.drop(keepCount).forEach { name ->
            val path = "$avatarDir/$name"
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                fm.removeItemAtPath(path, err.ptr)
            }
        }
    }
}

private fun topMostViewController(): UIViewController? {
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }

    val rootVC = windowScene?.keyWindow?.rootViewController ?: return null

    var topVC: UIViewController = rootVC
    while (true) {
        val presented = topVC.presentedViewController ?: break
        topVC = presented
    }
    return topVC
}
