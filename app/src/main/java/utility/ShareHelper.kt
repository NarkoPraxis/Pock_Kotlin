package utility

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

object ShareHelper {

    fun shareAppPromo(activity: Activity, onTargetSelected: (() -> Unit)? = null) {
        val message = "Just played Pock — 2-player puck battle on one phone! [PLAY_STORE_LINK]"
        launchShareSheet(activity, message, onTargetSelected)
    }

    fun shareScore(
        activity: Activity,
        highScore: Int,
        lowScore: Int,
        onTargetSelected: (() -> Unit)? = null
    ) {
        val message = "I just played Pock! Final score: $highScore – $lowScore. " +
            "Grab it on Google Play: [PLAY_STORE_LINK] #PockGame"
        launchShareSheet(activity, message, onTargetSelected)
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
            val action = "com.example.puck.SHARE_TARGET_SELECTED_${System.currentTimeMillis()}"

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
