@file:OptIn(ExperimentalForeignApi::class)
package utility

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSUserDefaults
import platform.posix.gettimeofday
import platform.posix.timeval

actual object PlatformStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun initialize(context: Any?) { /* no-op on iOS */ }

    private fun k(store: String, key: String) = "${store}_$key"

    actual fun saveInt(store: String, key: String, value: Int) =
        defaults.setInteger(value.toLong(), k(store, key))
    actual fun getInt(store: String, key: String, default: Int): Int {
        val fullKey = k(store, key)
        return if (defaults.objectForKey(fullKey) != null) defaults.integerForKey(fullKey).toInt() else default
    }
    actual fun saveString(store: String, key: String, value: String) =
        defaults.setObject(value, k(store, key))
    actual fun getString(store: String, key: String, default: String) =
        defaults.stringForKey(k(store, key)) ?: default
    actual fun saveBoolean(store: String, key: String, value: Boolean) =
        defaults.setBool(value, k(store, key))
    actual fun getBoolean(store: String, key: String, default: Boolean): Boolean {
        val fullKey = k(store, key)
        return if (defaults.objectForKey(fullKey) != null) defaults.boolForKey(fullKey) else default
    }
    actual fun saveLong(store: String, key: String, value: Long) =
        defaults.setInteger(value, k(store, key))
    actual fun getLong(store: String, key: String, default: Long): Long {
        val fullKey = k(store, key)
        return if (defaults.objectForKey(fullKey) != null) defaults.integerForKey(fullKey) else default
    }
    actual fun saveFloat(store: String, key: String, value: Float) =
        defaults.setFloat(value, k(store, key))
    actual fun getFloat(store: String, key: String, default: Float): Float {
        val fullKey = k(store, key)
        return if (defaults.objectForKey(fullKey) != null) defaults.floatForKey(fullKey) else default
    }
    actual fun currentTimeMs(): Long = memScoped {
        val tv = alloc<timeval>()
        gettimeofday(tv.ptr, null)
        tv.tv_sec * 1000L + tv.tv_usec / 1000L
    }
}
