package org.kasumi321.ushio.phitracker.data.platform

import platform.Foundation.NSLog
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelAlert
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private var nativeAlertWindow: UIWindow? = null

actual fun showPlatformMessage(message: String) {
    showNativeAlert(title = null, message = message)
}

internal fun showNativeAlert(title: String?, message: String) {
    dispatch_async(dispatch_get_main_queue()) {
        val windowScene = findActiveWindowScene()
        if (windowScene == null) {
            NSLog("Unable to present native alert: no active UIWindowScene")
            return@dispatch_async
        }

        val presenter = UIViewController()
        val window = UIWindow(windowScene = windowScene).apply {
            rootViewController = presenter
            windowLevel = UIWindowLevelAlert + 1.0
            makeKeyAndVisible()
        }
        nativeAlertWindow = window

        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = "确定",
                style = UIAlertActionStyleDefault,
                handler = {
                    nativeAlertWindow?.hidden = true
                    nativeAlertWindow = null
                },
            ),
        )
        presenter.presentViewController(alert, animated = true, completion = null)
    }
}

private fun findActiveWindowScene(): UIWindowScene? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    return scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: scenes.firstOrNull { it.keyWindow != null }
        ?: scenes.firstOrNull()
}
