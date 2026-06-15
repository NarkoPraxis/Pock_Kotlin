package utility

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

actual object ShareHelper {

    // TODO(launch): replace with the live Google Play listing URL once the app is published.
    // The id below is the production applicationId, so this resolves correctly the moment the
    // listing goes live. See Plans/STEPS_TO_LAUNCH/connect share functionality.md
    actual val storeUrl: String =
        "https://play.google.com/store/apps/details?id=com.runoutzone.pockpock"

    actual fun shareAppPromo(message: String, onShared: () -> Unit) {
        val activity = AdActivityProvider.activity ?: return
        launchShareSheet(activity, message, onShared)
    }

    private fun launchShareSheet(
        activity: Activity,
        text: String,
        onTargetSelected: (() -> Unit)?
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        if (onTargetSelected != null) {
            // Use a unique action so concurrent share intents don't collide.
            val action = "com.runoutzone.pockpock.SHARE_TARGET_SELECTED_${System.currentTimeMillis()}"

            // One-shot receiver: fires when the user selects a target app from the chooser.
            // This is the closest Android's standard share sheet lets us get to "user completed
            // a share" without integrating platform-specific SDKs.
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onTargetSelected()
                    try { activity.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(
                    receiver,
                    IntentFilter(action),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                activity.registerReceiver(receiver, IntentFilter(action))
            }

            // FLAG_MUTABLE is required: the system fills in EXTRA_CHOSEN_COMPONENT when it fires
            // the callback, which it cannot do on an immutable PendingIntent.
            // The package name must be set on the intent: Android 12+ rejects FLAG_MUTABLE on
            // implicit intents (no component/package), which would throw IllegalArgumentException.
            val pendingIntent = PendingIntent.getBroadcast(
                activity,
                0,
                Intent(action).apply { `package` = activity.packageName },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            activity.startActivity(
                Intent.createChooser(sendIntent, "Share via", pendingIntent.intentSender)
            )
        } else {
            activity.startActivity(Intent.createChooser(sendIntent, "Share via"))
        }
    }
}
