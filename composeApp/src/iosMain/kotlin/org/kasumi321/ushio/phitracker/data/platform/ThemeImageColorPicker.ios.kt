@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual val shouldShowThemeColorSourceSetting: Boolean = true

@Composable
actual fun rememberThemeImageColorPicker(onResult: (ThemeImageColorPickResult?) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val delegate = remember { ThemeImageColorPickerDelegate { currentOnResult(it) } }
    return {
        dispatch_async(dispatch_get_main_queue()) {
            delegate.launchPicker()
        }
    }
}

private class ThemeImageColorPickerDelegate(
    private val onResult: (ThemeImageColorPickResult?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    private var pickerWindow: UIWindow? = null
    private var previousKeyWindow: UIWindow? = null
    private var strongRef: ThemeImageColorPickerDelegate? = null

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
        val picker = PHPickerViewController(configuration).apply {
            delegate = this@ThemeImageColorPickerDelegate
        }
        strongRef = this
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)

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
            val image = data?.let { decodeImageBitmapFromData(it) }
            dispatch_async(dispatch_get_main_queue()) {
                releasePickerWindow()
                onResult(image?.let { ThemeImageColorPickResult(uri = null, image = it) })
            }
        }
    }

    private fun releasePickerWindow() {
        pickerWindow?.setHidden(true)
        pickerWindow?.rootViewController = null
        pickerWindow = null
        previousKeyWindow?.makeKeyAndVisible()
        previousKeyWindow = null
        strongRef = null
    }
}

private fun findActiveWindowScene(): UIWindowScene? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    return scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: scenes.firstOrNull { it.keyWindow != null }
        ?: scenes.firstOrNull()
}

private fun decodeImageBitmapFromData(data: NSData): ImageBitmap? {
    val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return null
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}
