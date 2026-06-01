package com.runoutzone.pockpock.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.runoutzone.pockpock.LocalDarkMode
import utility.PaintBucket

/**
 * Shown when a player taps a 100%-only locked item (custom color, premium styles). Explains the
 * content unlocks when the unlock meter reaches 100% — no ad is offered.
 */
@Composable
fun MeterLockedPopup(currentPercent: Int, onDismiss: () -> Unit) {
    val isDark = LocalDarkMode.current
    val bg = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
    val fg = if (isDark) PaintBucket.white else Color(0xFF222222)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = fg) } },
        title = { Text("Locked", color = fg) },
        text = {
            Text(
                "This unlocks when your unlock meter reaches 100%. " +
                "You're at $currentPercent% right now.",
                color = fg
            )
        },
        containerColor = bg,
        titleContentColor = fg,
        textContentColor = fg
    )
}
