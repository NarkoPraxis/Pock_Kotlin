@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package utility

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

object IosShareHelper {
    fun shareAppPromo(onCompleted: (Boolean) -> Unit) {
        val message = "Just played Pock — 2-player puck battle on one phone! [APP_STORE_LINK]"
        present(message, onCompleted)
    }

    @Suppress("UNCHECKED_CAST")
    private fun present(text: String, onCompleted: (Boolean) -> Unit) {
        val items = listOf<Any>(text)
        val vc = UIActivityViewController(activityItems = items, applicationActivities = null)
        vc.completionWithItemsHandler = { _, completed, _, _ ->
            onCompleted(completed)
        }
        val root = topViewController()
        if (root == null) {
            onCompleted(false)
            return
        }
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
