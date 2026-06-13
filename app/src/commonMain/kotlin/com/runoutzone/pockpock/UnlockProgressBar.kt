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
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Single-tint + transparency model: [meterColor] tints both the outer ring and the earned fill;
 * the unearned remainder draws *nothing* so whatever sits behind the bar shows through. This makes
 * the bar theme-responsive with no per-mode colors — on the main menu it sits on the red customize
 * pill (so the empty side reads red, identical to before) and in the designer screens it sits on the
 * screen background (so the empty side reads white in light mode / dark-blue in dark mode).
 *
 * The percentage label is constrained to half the width so it never collides with the fill front:
 * at/above 50% it sits left over the fill in [filledLabelColor]; below 50% it sits right over the
 * transparent remainder in [emptyLabelColor] (the caller picks a color readable against its backdrop).
 */
@Composable
fun UnlockThermometer(
    progress: Int,
    meterColor: Color,
    filledLabelColor: Color,
    emptyLabelColor: Color,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0, 100)
    val frac = clamped / 100f
    val aboveHalf = clamped >= 50

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val barH = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Outer ring — a stroke (not a filled shape) so the pill interior stays transparent and
            // the backdrop shows through wherever there's no fill.
            val ring = h * 0.055f
            val ringInset = ring / 2f
            drawRoundRect(
                color = meterColor,
                topLeft = Offset(ringInset, ringInset),
                size = Size(w - ring, h - ring),
                cornerRadius = CornerRadius((h - ring) / 2f, (h - ring) / 2f),
                style = Stroke(width = ring)
            )
            // Progress fill, inset past the ring (leaving a thin transparent gap between ring and
            // fill). Clipped to the earned fraction; the remainder is left transparent.
            val s2 = ring * 2f
            val innerW2 = w - 2 * s2
            val innerH2 = h - 2 * s2
            val fillW = innerW2 * frac
            if (fillW > 0f && innerW2 > 0f && innerH2 > 0f) {
                clipRect(left = s2, top = s2, right = s2 + fillW, bottom = s2 + innerH2) {
                    drawRoundRect(
                        color = meterColor,
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
                color = if (aboveHalf) filledLabelColor else emptyLabelColor,
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
 * Standalone thermometer for the Ball Designer / Color screens. Tinted brand-red and drawn with a
 * transparent remainder so it reads against the screen background in both themes. The filled-side
 * label is white (over the red fill); the empty-side label is red on the light background but flips
 * to white in dark mode, where red-on-dark-blue would be too low-contrast.
 */
@Composable
fun UnlockProgressBar(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkMode.current
    UnlockThermometer(
        progress = progress,
        meterColor = PaintBucket.menuAccentRed,
        filledLabelColor = PaintBucket.white,
        emptyLabelColor = if (isDark) PaintBucket.white else PaintBucket.menuAccentRed,
        fontFamily = poppinsFamily(),
        modifier = modifier
    )
}
