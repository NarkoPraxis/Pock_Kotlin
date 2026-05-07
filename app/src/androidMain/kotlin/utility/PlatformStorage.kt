package utility

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

actual object PlatformStorage {
    private lateinit var adPrefs: SharedPreferences
    private lateinit var settingsPrefs: SharedPreferences

    actual fun initialize(context: Any?) {
        val ctx = context as Context
        adPrefs = ctx.getSharedPreferences("adPreferences", Context.MODE_PRIVATE)
        settingsPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    }

    private fun prefs(store: String) = if (store == "ad") adPrefs else settingsPrefs

    actual fun saveInt(store: String, key: String, value: Int) =
        prefs(store).edit().putInt(key, value).apply()
    actual fun getInt(store: String, key: String, default: Int) =
        prefs(store).getInt(key, default)
    actual fun saveString(store: String, key: String, value: String) =
        prefs(store).edit().putString(key, value).apply()
    actual fun getString(store: String, key: String, default: String) =
        prefs(store).getString(key, default) ?: default
    actual fun saveBoolean(store: String, key: String, value: Boolean) =
        prefs(store).edit().putBoolean(key, value).apply()
    actual fun getBoolean(store: String, key: String, default: Boolean) =
        prefs(store).getBoolean(key, default)
    actual fun saveLong(store: String, key: String, value: Long) =
        prefs(store).edit().putLong(key, value).apply()
    actual fun getLong(store: String, key: String, default: Long) =
        prefs(store).getLong(key, default)
    actual fun saveFloat(store: String, key: String, value: Float) =
        prefs(store).edit().putFloat(key, value).apply()
    actual fun getFloat(store: String, key: String, default: Float) =
        prefs(store).getFloat(key, default)
    actual fun currentTimeMs(): Long = System.currentTimeMillis()
}
