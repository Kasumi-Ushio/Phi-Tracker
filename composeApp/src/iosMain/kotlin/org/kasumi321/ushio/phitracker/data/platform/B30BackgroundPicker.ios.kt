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
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Clock

@Composable
actual fun rememberB30BackgroundPicker(onResult: (String?) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val delegate = remember { B30BackgroundPickerDelegate { currentOnResult(it) } }

    return {
        dispatch_async(dispatch_get_main_queue()) {
            delegate.launchPicker()
        }
    }
}

private class B30BackgroundPickerDelegate(
    private val onResult: (String?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    private var picker: PHPickerViewController? = null
    private var pickerWindow: UIWindow? = null
    private var previousKeyWindow: UIWindow? = null
    private var strongRef: B30BackgroundPickerDelegate? = null

    fun launchPicker() {
        val windowScene = findActiveWindowScene()
            ?: run {
                onResult(null)
                return
            }

        val presenter = UIViewController()
        previousKeyWindow = windowScene.keyWindow
        pickerWindow = UIWindow(windowScene = windowScene).apply {
            rootViewController = presenter
            windowLevel = UIWindowLevelNormal
            makeKeyAndVisible()
        }

        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val imagePicker = PHPickerViewController(configuration).apply {
            delegate = this@B30BackgroundPickerDelegate
        }
        picker = imagePicker
        strongRef = this
        presenter.presentViewController(imagePicker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        this.picker = null

        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            releasePickerWindow()
            onResult(null)
            return
        }

        val typeIdentifier = result.itemProvider.registeredTypeIdentifiers
            .filterIsInstance<String>()
            .firstOrNull { it == UTTypeImage.identifier || it.startsWith("public.image") || it.startsWith("public.") }
            ?: UTTypeImage.identifier

        result.itemProvider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data, _ ->
            val savedPath = data?.let { saveBackgroundData(it) }
            dispatch_async(dispatch_get_main_queue()) {
                releasePickerWindow()
                onResult(savedPath)
            }
        }
    }

    private fun releasePickerWindow() {
        picker = null
        pickerWindow?.setHidden(true)
        pickerWindow?.rootViewController = null
        pickerWindow = null
        previousKeyWindow?.makeKeyAndVisible()
        previousKeyWindow = null
        strongRef = null
    }

    private fun saveBackgroundData(data: NSData): String? {
        return runCatching {
            val paths = createPlatformPaths()
            val bgDir = "${paths.filesDir}/b30-backgrounds"
            val fm = NSFileManager.defaultManager
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                if (!fm.fileExistsAtPath(bgDir)) {
                    fm.createDirectoryAtPath(
                        path = bgDir,
                        withIntermediateDirectories = true,
                        attributes = null,
                        error = err.ptr
                    )
                }
            }

            val image = UIImage.imageWithData(data)
                ?: throw RuntimeException("Failed to decode selected background image")
            val pngData = UIImagePNGRepresentation(image)
                ?: throw RuntimeException("Failed to convert selected background image to PNG")
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val fileName = "bg_$timestamp.png"
            val filePath = "$bgDir/$fileName"
            val fileUrl = NSURL.fileURLWithPath(filePath)
            check(pngData.writeToURL(fileUrl, atomically = true)) {
                "Failed to write background image to file"
            }
            cleanupOldBackgrounds(bgDir, keepCount = 3)
            filePath
        }.getOrNull()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cleanupOldBackgrounds(bgDir: String, keepCount: Int) {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(bgDir, null)
            ?.filterIsInstance<String>()
            ?.filter { it.startsWith("bg_") && it.endsWith(".png") }
            ?: return

        if (contents.size <= keepCount) return

        val sorted = contents.sortedByDescending { name ->
            name.removePrefix("bg_").removeSuffix(".png").toLongOrNull() ?: 0L
        }

        sorted.drop(keepCount).forEach { name ->
            val path = "$bgDir/$name"
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                fm.removeItemAtPath(path, err.ptr)
            }
        }
    }
}

private fun findActiveWindowScene(): UIWindowScene? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    return scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: scenes.firstOrNull { it.keyWindow != null }
        ?: scenes.firstOrNull()
}
