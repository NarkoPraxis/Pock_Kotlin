package com.runoutzone.pockpock.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.LocalDarkMode

/**
 * Visual state of a style/color option in a carousel.
 * - [UnlockedSelected]  : the chosen option (accent ring, raised emphasis).
 * - [UnlockedAvailable] : selectable, not current (light option chrome). Tap selects.
 * - [LockedAd]          : raised button; tap watches a rewarded ad to unlock this one item.
 * - [LockedPremium]     : raised button; tap shows the "complete the meter" popup (no ad).
 */
enum class OptionLockState { UnlockedSelected, UnlockedAvailable, LockedAd, LockedPremium }

/**
 * Square, theme-matched option button. Raised when idle, pressed-in when held, with a drop shadow.
 * The [preview] (a Canvas drawing a ball part or color swatch) is clipped to the rounded top face
 * so it can't spill outside the button bounds.
 */
@Composable
fun StyleOptionButton(
    state: OptionLockState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    enabled: Boolean = true,
    preview: @Composable BoxScope.() -> Unit,
) {
    val isDark = LocalDarkMode.current
    var pressed by remember { mutableStateOf(false) }

    val locked   = state == OptionLockState.LockedAd || state == OptionLockState.LockedPremium
    val selected = state == OptionLockState.UnlockedSelected

    // Locked items read as chunky buttons (higher rest elevation); unlocked options read flat.
    val restElevation = if (locked) 6.dp else if (selected) 3.dp else 1.dp
    val elevation by animateDpAsState(if (pressed) 0.dp else restElevation, label = "elev")

    val shape     = RoundedCornerShape(14.dp)
    val faceColor = if (isDark) Color(0xFF1A2A3E) else Color(0xFFE8E8F0)
    val accent    = Color(0xFF52B6F2)

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation, shape)
            .clip(shape)
            .background(faceColor)
            .then(if (selected) Modifier.border(3.dp, accent, shape) else Modifier)
            .pointerInput(state, enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { if (enabled) onTap() }
                )
            }
    ) {
        // Style/color preview, clipped to the rounded face.
        Box(
            modifier = Modifier.fillMaxSize().clip(shape),
            contentAlignment = Alignment.Center,
            content = preview
        )

        // Dim + affordance for locked items.
        if (locked) {
            val dim = if (enabled) 0.30f else 0.55f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            val label = if (state == OptionLockState.LockedPremium) "🔒 100%" else "▶ AD"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
