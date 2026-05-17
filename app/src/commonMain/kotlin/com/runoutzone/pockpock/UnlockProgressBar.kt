package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import utility.PaintBucket
import utility.Storage

/**
 * Sideways-thermometer progress bar for the ball unlock system.
 *
 * Horizontal tube filling left-to-right, with a circle bulb on the right showing the current
 * percentage. Nine tick marks at 10% intervals indicate each ball-type unlock threshold.
 *
 * Palette color (outline, bulb, ticks) is randomly chosen once per composition as either
 * the high-player warm or low-player cold stroke color. Fill is always the purple effectColor.
 */
@Composable
fun UnlockProgressBar(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0, 100)
    val useHighPlayer = remember { kotlin.random.Random.nextBoolean() }
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val isDark = try { Storage.darkMode } catch (_: Exception) { false }

        val palette = if (useHighPlayer) PaintBucket.highBallStroke else PaintBucket.lowBallStroke
        val fillColor = PaintBucket.effectColor

        val paletteDim = palette.copy(alpha = 80f / 255f)
        val tickOnFill = PaintBucket.white.copy(alpha = 200f / 255f)
        val bgAlpha = if (isDark) 35f / 255f else 18f / 255f
        val tubeEmptyBg = palette.copy(alpha = bgAlpha)

        val pad = h * 0.10f
        val strokeW = h * 0.075f

        // Bulb (circle on the right)
        val bulbR = h / 2f - pad
        val bulbCx = w - h / 2f
        val bulbCy = h / 2f

        // Tube — right edge overlaps the bulb slightly so they merge seamlessly
        val tubeH = h * 0.34f
        val tubeLeft = pad * 2f
        val tubeRight = bulbCx + bulbR * 0.35f
        val tubeTop = bulbCy - tubeH / 2f
        val tubeBottom = bulbCy + tubeH / 2f
        val tubeCorner = tubeH / 2f
        val fillableWidth = bulbCx - tubeLeft

        // 1. Empty-tube background
        drawRoundRect(
            color = tubeEmptyBg,
            topLeft = Offset(tubeLeft, tubeTop),
            size = Size(tubeRight - tubeLeft, tubeH),
            cornerRadius = CornerRadius(tubeCorner)
        )

        // 2. Fill (purple effectColor)
        if (clampedProgress > 0) {
            val fillRight = (tubeLeft + fillableWidth * clampedProgress / 100f)
                .coerceAtLeast(tubeLeft + tubeCorner * 2f)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(tubeLeft, tubeTop),
                size = Size(fillRight - tubeLeft, tubeH),
                cornerRadius = CornerRadius(tubeCorner)
            )
        }

        // 3. Tick marks at 10% intervals, clipped just inside tube bounds
        val tickStrokeW = strokeW * 0.55f
        val tickInset = strokeW * 0.4f
        for (i in 1..9) {
            val tickX = tubeLeft + fillableWidth * i / 10f
            drawLine(
                color = if (clampedProgress >= i * 10) tickOnFill else paletteDim,
                start = Offset(tickX, tubeTop + tickInset),
                end = Offset(tickX, tubeBottom - tickInset),
                strokeWidth = tickStrokeW
            )
        }

        // 4. Tube outline (drawn after fill so border sits on top)
        drawRoundRect(
            color = palette,
            topLeft = Offset(tubeLeft, tubeTop),
            size = Size(tubeRight - tubeLeft, tubeH),
            cornerRadius = CornerRadius(tubeCorner),
            style = Stroke(width = strokeW)
        )

        // 5. Bulb — palette fill then palette outline
        drawCircle(color = palette, radius = bulbR, center = Offset(bulbCx, bulbCy))
        drawCircle(
            color = palette,
            radius = bulbR,
            center = Offset(bulbCx, bulbCy),
            style = Stroke(width = strokeW)
        )

        // 6. Percentage label centred inside the bulb
        val pxPerSp = density * fontScale
        val textSizeSp = (bulbR * 0.72f / pxPerSp)
        val measured = textMeasurer.measure(
            "$clampedProgress%",
            TextStyle(
                color = PaintBucket.white,
                fontSize = textSizeSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        drawText(
            measured,
            topLeft = Offset(
                bulbCx - measured.size.width / 2f,
                bulbCy - measured.size.height / 2f
            )
        )
    }
}
