package utility

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Holds an application Context for the dev profiler's file writer. Set once from MainActivity.onCreate.
 * Dev-only; never relied on by shipping code (the profiler early-returns when disabled).
 */
object ProfileContext {
    var appContext: Context? = null
}

/** Public subfolder (under Downloads) the profiler writes into, so it's visible in any file browser. */
private const val PROFILE_FOLDER = "pokpok_profiling"

actual fun readGcCount(): Long =
    Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: -1L

/**
 * Writes the profile JSON to a user-visible location instead of the app-private external dir
 * (Android/data/<pkg>, which the Files app hides on modern Android).
 *
 * API 29+ : MediaStore Downloads with RELATIVE_PATH "Download/pokpok_profiling" — no permission
 *           needed, shows up as Internal storage ▸ Download ▸ pokpok_profiling.
 * API 26–28: legacy public Downloads dir (best effort; needs WRITE_EXTERNAL_STORAGE granted).
 * Either failure falls back to the app-private dir so a capture is never silently lost.
 */
actual fun writeProfileFile(name: String, contents: String): String {
    val ctx = ProfileContext.appContext ?: return ""
    val bytes = contents.encodeToByteArray()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$PROFILE_FOLDER"
                )
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                return "Internal storage/${Environment.DIRECTORY_DOWNLOADS}/$PROFILE_FOLDER/$name"
            }
        } catch (_: Exception) { /* fall through to app-private dir */ }
    } else {
        try {
            @Suppress("DEPRECATION")
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(pub, PROFILE_FOLDER).apply { mkdirs() }
            val f = File(dir, name)
            f.writeBytes(bytes)
            return f.absolutePath
        } catch (_: Exception) { /* fall through to app-private dir */ }
    }

    // Fallback: app-private external dir (pullable via adb, even if hidden in the Files app).
    val dir = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).let { File(it, "profiles").apply { mkdirs() } }
    val f = File(dir, name)
    f.writeText(contents)
    return f.absolutePath
}

actual fun profileDeviceDescription(): String =
    "${Build.MODEL} / Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"

actual fun profileAppVersion(): String {
    val ctx = ProfileContext.appContext ?: return "unknown"
    return try {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val code = pkg.versionCode
        "${pkg.versionName} (build $code)"
    } catch (e: Exception) {
        "unknown"
    }
}
