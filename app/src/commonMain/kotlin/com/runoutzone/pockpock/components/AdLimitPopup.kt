package com.runoutzone.pockpock.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import utility.PaintBucket

/**
 * Shown when the player tries to unlock something via ad but has hit the hourly ad limit.
 * Replaces the old main-menu "next ad in X" button message.
 */
@Composable
fun AdLimitPopup(minutesUntil: Long, onDismiss: () -> Unit) {
    val t = if (minutesUntil >= 60) "${minutesUntil / 60}h ${minutesUntil % 60}m" else "${minutesUntil}m"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Ad limit reached") },
        text = { Text("You've watched the maximum number of ads for now. Next ad available in $t.") },
        containerColor = PaintBucket.menuButtonDark
    )
}
