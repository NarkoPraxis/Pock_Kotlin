package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.menu.poppinsFamily
import utility.PaintBucket

/**
 * Round-pill unlock thermometer. Shared by the main menu (sitting inside the red customize pill)
 * and the Ball Designer / Color screens (standalone at the top of the screen).
 *
 * Layered: [outlineColor] outer ring → brand-red ring (red outline + unfilled background) → a white
 * progress fill clipped to the earned fraction. The percentage label is constrained to half the
 * width so it never collides with the fill front: at/above 50% the left half is white so the label
 * sits left in red text; below 50% it sits right in white text over the red remainder.
 *
 * Colors are the fixed brand red/white — the thermometer never adopts the player's custom colors.
 * Only [outlineColor] varies with context (white when in front of the red menu pill; the screen's
 * dark-mode background when standalone) so the outer ring reads correctly against its backdrop.
 */
@Composable
fun UnlockThermometer(
    progress: Int,
    outlineColor: Color,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0, 100)
    val frac = clamped / 100f
    val aboveHalf = clamped >= 50
    val red = PaintBucket.menuAccentRed
    val white = PaintBucket.white

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val barH = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Outer ring — white when in front of the red menu pill, the screen's dark-mode
            // background when standalone, so the outline reads against whatever sits behind it.
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f)
            )
            // Red ring — doubles as both the red outline and the unfilled background.
            val s1 = h * 0.055f
            val innerH1 = h - 2 * s1
            drawRoundRect(
                color = red,
                topLeft = Offset(s1, s1),
                size = Size(w - 2 * s1, innerH1),
                cornerRadius = CornerRadius(innerH1 / 2f, innerH1 / 2f)
            )
            // White progress fill, inset by another red-stroke band so the red outline stays
            // visible around it. Clipped to the earned fraction; the remainder shows the red ring.
            val s2 = s1 + h * 0.055f
            val innerW2 = w - 2 * s2
            val innerH2 = h - 2 * s2
            val fillW = innerW2 * frac
            if (fillW > 0f) {
                clipRect(left = s2, top = s2, right = s2 + fillW, bottom = s2 + innerH2) {
                    drawRoundRect(
                        color = white,
                        topLeft = Offset(s2, s2),
                        size = Size(innerW2, innerH2),
                        cornerRadius = CornerRadius(innerH2 / 2f, innerH2 / 2f)
                    )
                }
            }
        }

        // Percentage label, restricted to half the width on the side opposite the fill front.
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = barH * 0.42f),
            contentAlignment = if (aboveHalf) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Text(
                text = "$clamped%",
                color = if (aboveHalf) red else white,
                modifier = Modifier.fillMaxWidth(0.5f),
                textAlign = if (aboveHalf) TextAlign.Start else TextAlign.End,
                fontSize = (barH.value * 0.4f).sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
        }
    }
}

/**
 * Standalone thermometer for the Ball Designer / Color screens. The exact main-menu thermometer,
 * minus the red pill behind it: the only difference is the outer ring, which uses the screen's
 * dark-mode background in dark mode (white in light mode) since it sits directly on the screen.
 */
@Composable
fun UnlockProgressBar(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkMode.current
    UnlockThermometer(
        progress = progress,
        outlineColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.white,
        fontFamily = poppinsFamily(),
        modifier = modifier
    )
}
