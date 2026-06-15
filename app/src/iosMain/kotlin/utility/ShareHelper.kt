@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package utility

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

actual object ShareHelper {

    // TODO(launch): replace with the live App Store listing URL once App Store Connect assigns
    // the numeric Apple ID (apps.apple.com/app/id<APPLE_ID>).
    // See Plans/STEPS_TO_LAUNCH/connect share functionality.md
    actual val storeUrl: String =
        "https://apps.apple.com/app/idAPPLE_ID_PLACEHOLDER"

    actual fun shareAppPromo(message: String, onShared: () -> Unit) {
        present(message, onShared)
    }

    @Suppress("UNCHECKED_CAST")
    private fun present(text: String, onCompleted: () -> Unit) {
        val items = listOf<Any>(text)
        val vc = UIActivityViewController(activityItems = items, applicationActivities = null)
        vc.completionWithItemsHandler = { _, completed, _, _ ->
            if (completed) onCompleted()
        }
        val root = topViewController()
        if (root == null) return
        root.presentViewController(vc, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        val windows = UIApplication.sharedApplication.windows
        val keyWindow = windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
            ?: windows.firstOrNull() as? UIWindow
            ?: return null
        var top: UIViewController? = keyWindow.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}
