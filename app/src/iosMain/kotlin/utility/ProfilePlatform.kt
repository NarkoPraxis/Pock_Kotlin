@file:OptIn(ExperimentalForeignApi::class)
package utility

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice

// ART-specific; iOS uses ARC, no equivalent runtime GC counter.
actual fun readGcCount(): Long = -1L

actual fun writeProfileFile(name: String, contents: String): String {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: return ""
    val path = "$docs/$name"
    val ok = (contents as NSString).writeToFile(
        path, atomically = true, encoding = NSUTF8StringEncoding, error = null
    )
    return if (ok) path else ""
}

actual fun profileDeviceDescription(): String {
    val device = UIDevice.currentDevice
    return "${device.model} / ${device.systemName} ${device.systemVersion}"
}

actual fun profileAppVersion(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val version = info?.get("CFBundleShortVersionString") as? String ?: "?"
    val build = info?.get("CFBundleVersion") as? String ?: "?"
    return "$version (build $build)"
}
