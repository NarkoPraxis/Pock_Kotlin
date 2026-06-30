package com.runoutzone.pockpock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.menu.poppinsFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.app_title
import pock_kotlin.app.generated.resources.epilepsy_warning
import pock_kotlin.app.generated.resources.splash_logo

// How long the in-app splash lingers before handing off to the main menu. The OS shows its own
// (uncustomizable) icon splash first; this continues it just long enough for the photosensitivity
// notice to be readable without delaying the player.
private const val SPLASH_HOLD_MS = 2200L

// Launcher-icon colors, mirrored here so the in-app splash matches the system icon exactly:
// red adaptive background (#f25252) behind the blue-bird foreground (splash_logo, copied verbatim
// from ic_launcher_foreground). The field follows the in-app dark-mode toggle: white in light,
// black in dark (matching the day/night system-splash background).
private val ICON_BG = Color(0xFFF25252)

// Shown bottom-right on the splash so the running build — and its headline feature — is obvious.
private const val VERSION_LABEL = "v0.9 Score Optimisation"

/**
 * Custom splash shown as the app's first destination. It deliberately replicates the system launch
 * screen — the launcher icon centered on a white field — and adds the photosensitivity warning
 * underneath, which the Android 12+ system splash API cannot do. Auto-advances to the main menu.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val poppins = poppinsFamily()
    val isDark = LocalDarkMode.current
    val bgColor = if (isDark) Color.Black else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF222222)

    LaunchedEffect(Unit) {
        delay(SPLASH_HOLD_MS)
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Rounded-square launcher icon: red background + bird foreground filling it (the
            // foreground vector already carries the adaptive safe-zone padding, so filling the
            // square reproduces the launcher art).
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(RoundedCornerShape(percent = 22))
                    .background(ICON_BG),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.splash_logo),
                    contentDescription = stringResource(Res.string.app_title),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(Res.string.epilepsy_warning),
                color = textColor,
                fontFamily = poppins,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .widthIn(max = 360.dp)
            )
        }

        // Version label, bottom-right: makes the build immediately identifiable and surfaces its
        // headline feature. Dimmed so it never competes with the warning above.
        Text(
            text = VERSION_LABEL,
            color = textColor.copy(alpha = 0.5f),
            fontFamily = poppins,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
