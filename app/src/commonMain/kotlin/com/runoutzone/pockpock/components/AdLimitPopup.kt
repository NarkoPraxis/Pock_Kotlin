package com.runoutzone.pockpock.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.runoutzone.pockpock.LocalDarkMode
import utility.PaintBucket

/**
 * Shown when the player tries to unlock something via ad but has hit the hourly ad limit.
 * Replaces the old main-menu "next ad in X" button message.
 */
@Composable
fun AdLimitPopup(minutesUntil: Long, onDismiss: () -> Unit) {
    val isDark = LocalDarkMode.current
    val bg = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
    val fg = if (isDark) PaintBucket.white else Color(0xFF222222)
    val t = if (minutesUntil >= 60) "${minutesUntil / 60}h ${minutesUntil % 60}m" else "${minutesUntil}m"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = fg) } },
        title = { Text("Ad limit reached", color = fg) },
        text = { Text("You've watched the maximum number of ads for now. Next ad available in $t.", color = fg) },
        containerColor = bg,
        titleContentColor = fg,
        textContentColor = fg
    )
}
