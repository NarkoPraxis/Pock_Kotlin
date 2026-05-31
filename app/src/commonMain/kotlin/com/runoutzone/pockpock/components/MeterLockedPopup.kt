package com.runoutzone.pockpock.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import utility.PaintBucket

/**
 * Shown when a player taps a 100%-only locked item (custom color, premium styles). Explains the
 * content unlocks when the unlock meter reaches 100% — no ad is offered.
 */
@Composable
fun MeterLockedPopup(currentPercent: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = { Text("Locked") },
        text = {
            Text(
                "This unlocks when your unlock meter reaches 100%. " +
                "You're at $currentPercent% right now."
            )
        },
        containerColor = PaintBucket.menuButtonDark
    )
}
