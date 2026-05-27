package shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import gameobjects.Settings
import kotlin.math.abs

class ColorCarousel(initialHue: Float = 0f) : ScrollSnapCarousel() {

    companion object {
        const val CUSTOM_IDX = 9
        const val CUSTOM_UNLOCK_PCT = 100

        data class Preset(val hue: Float, val unlockPct: Int)
        val PRESETS: Array<Preset?> = arrayOf(
            Preset(  0f,  0),   // Red
            Preset( 30f,  5),   // Orange
            Preset( 60f, 15),   // Yellow
            Preset(120f, 25),   // Green
            Preset(202.5f, 35), // Sky Blue (low player default)
            Preset(240f,  0),   // Blue
            Preset(270f, 45),   // Purple
            Preset(300f, 55),   // Magenta
            Preset(330f, 65),   // Pink
            null,               // Custom
        )
    }

    override val itemCount: Int = 10
    override val slotW: Float get() = Settings.screenRatio * 3f
    override val cx: Float get() = Settings.middleX

    var isUpsideDown: Boolean = false
    var currentHue: Float = initialHue
        private set

    val isCustomSelected: Boolean get() = snapIndex == CUSTOM_IDX
    var isCustomSliderActive: Boolean = false
        private set
    var hasCustomSelection: Boolean = false
        private set

    val h: Float get() = Settings.screenRatio * 5f
    val w: Float get() = Settings.screenWidth

    override fun toLogicalX(screenX: Float): Float =
        if (isUpsideDown) 2f * Settings.middleX - screenX else screenX

    override fun onSnappedTo(index: Int) {
        if (index == CUSTOM_IDX) {
            isCustomSliderActive = true
        } else {
            isCustomSliderActive = false
            if (isUnlocked(index)) {
                currentHue = PRESETS[index]?.hue ?: currentHue
            }
        }
    }

    fun setCustomHue(hue: Float) {
        currentHue = hue.coerceIn(0f, 360f)
        hasCustomSelection = true
    }

    fun deactivateCustomSlider() {
        isCustomSliderActive = false
    }

    fun tryActivateCustomSlider() {
        if (isCustomSelected) isCustomSliderActive = true
    }

    fun isUnlocked(index: Int): Boolean = when {
        index == CUSTOM_IDX -> Settings.unlockProgress >= CUSTOM_UNLOCK_PCT
        index in 0..8       -> Settings.unlockProgress >= (PRESETS[index]?.unlockPct ?: 0)
        else                -> false
    }

    fun initializeToHue(hue: Float) {
        currentHue = hue
        for (i in 0 until CUSTOM_IDX) {
            val presetHue = PRESETS[i]?.hue ?: continue
            if (abs(presetHue - hue) < 1f) {
                scrollToIndex(i)
                isCustomSliderActive = false
                return
            }
        }
        scrollToIndex(CUSTOM_IDX)
    }

    // Returns hue (0–360) from a screen X touch on the active custom slider, or -1 if not applicable.
    fun getHueFromSliderX(screenX: Float): Float {
        if (!isCustomSliderActive) return -1f
        val ratio = Settings.screenRatio
        val sliderLeft  = cx - w / 2f + ratio * 2f
        val sliderRight = cx + w / 2f - ratio * 2f
        val logicalX = toLogicalX(screenX)
        val t = ((logicalX - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
        return t * 360f
    }

    fun DrawScope.drawCarousel(centerY: Float, frame: Int, isDark: Boolean, label: String = "", textMeasurer: TextMeasurer? = null) {
        if (Settings.screenRatio == 0f) return
        val ratio  = Settings.screenRatio
        val halfW  = w / 2f
        val halfH  = h / 2f

        val bgColor = if (isDark) Color(0xFF0A0A14.toInt()) else Color.White
        drawRect(bgColor, topLeft = Offset(cx - halfW, centerY - halfH), size = Size(w, h))

        val borderColor = Color.hsv(currentHue, 0.661f, 0.961f)
        drawRect(borderColor,
            topLeft = Offset(cx - halfW, centerY - halfH),
            size    = Size(w, h),
            style   = Stroke(ratio * 0.25f))

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + ratio * 0.2f, centerY - halfH + ratio * 0.2f,
            cx + halfW - ratio * 0.2f, centerY + halfH - ratio * 0.2f
        ))
        if (isCustomSliderActive) drawHueSlider(centerY, ratio)
        else                      drawColorItems(centerY, ratio)
        canvas.restore()

        // Draw label tab after restore so it is drawn over items and not clipped
        if (label.isNotEmpty() && textMeasurer != null) {
            val density    = drawContext.density.density
            val fontSizeSp = (ratio * 0.6f / density).sp
            val textLayout = textMeasurer.measure(
                text  = label,
                style = TextStyle(color = Color.Black, fontSize = fontSizeSp, fontWeight = FontWeight.Bold)
            )
            val labelPadX = ratio * 0.45f
            val labelW    = textLayout.size.width + labelPadX * 2f
            val labelH    = (textLayout.size.height + ratio * 0.7f).coerceAtLeast(ratio * 1.8f)
            val lx        = cx - halfW
            val ty        = centerY - halfH
            val strokeW   = ratio * 0.25f

            // Redraw bg to cover any items drawn beneath the label area
            drawRect(bgColor, topLeft = Offset(lx, ty), size = Size(labelW, labelH))

            // Redraw carousel border on the two sides shared with the label (top + left)
            drawLine(borderColor, Offset(lx, ty), Offset(lx + labelW, ty), strokeW)
            drawLine(borderColor, Offset(lx, ty), Offset(lx, ty + labelH), strokeW)

            // Draw label's own inner borders (right edge and bottom edge)
            drawLine(borderColor, Offset(lx + labelW, ty), Offset(lx + labelW, ty + labelH), strokeW)
            drawLine(borderColor, Offset(lx, ty + labelH), Offset(lx + labelW, ty + labelH), strokeW)

            // Draw label text vertically centered inside the tab
            val textTopY = ty + (labelH - textLayout.size.height) / 2f
            drawText(textLayoutResult = textLayout, topLeft = Offset(lx + labelPadX, textTopY))
        }
    }

    private fun DrawScope.drawHueSlider(centerY: Float, ratio: Float) {
        val sliderLeft  = cx - w / 2f + ratio * 2f
        val sliderRight = cx + w / 2f - ratio * 2f
        val barH        = ratio * 1.4f

        val rainbowColors = (0..36).map { Color.hsv(it * 10f, 0.9f, 1f) }
        drawRect(
            brush   = Brush.linearGradient(rainbowColors,
                          start = Offset(sliderLeft, centerY), end = Offset(sliderRight, centerY)),
            topLeft = Offset(sliderLeft, centerY - barH / 2f),
            size    = Size(sliderRight - sliderLeft, barH)
        )
        // End-cap circles
        drawCircle(Color.hsv(0f,   0.9f, 1f), radius = barH / 2f, center = Offset(sliderLeft,  centerY))
        drawCircle(Color.hsv(350f, 0.9f, 1f), radius = barH / 2f, center = Offset(sliderRight, centerY))

        val thumbX = sliderLeft + (currentHue / 360f) * (sliderRight - sliderLeft)
        val thumbR = ratio * 1.5f
        drawCircle(Color.hsv(currentHue, 0.661f, 0.961f), radius = thumbR, center = Offset(thumbX, centerY))
        drawCircle(Color.White, radius = thumbR, center = Offset(thumbX, centerY),
            style = Stroke(ratio * 0.22f))
    }

    private fun DrawScope.drawColorItems(centerY: Float, ratio: Float) {
        val halfW        = w / 2f
        val circleRadius = ratio * 1.2f
        val centerIndex  = scrollX / slotW

        for (i in 0 until itemCount) {
            val slotCx = cx - scrollX + i * slotW
            if (slotCx < cx - halfW - slotW || slotCx > cx + halfW + slotW) continue

            val dist     = abs(i.toFloat() - centerIndex)
            val isCenter = dist < 0.5f

            val chipAlpha = if (isCenter) 0.22f else 0.07f
            val chipColor = if (isCenter) Color.hsv(currentHue, 0.359f, 0.961f).copy(alpha = chipAlpha)
                            else          Color.White.copy(alpha = chipAlpha)
            val chipHalfW  = slotW * if (isCenter) 0.45f else 0.42f
            val chipHeight = h - ratio * 2.5f
            drawRoundRect(
                color        = chipColor,
                topLeft      = Offset(slotCx - chipHalfW, centerY - chipHeight / 2f),
                size         = Size(chipHalfW * 2f, chipHeight),
                cornerRadius = CornerRadius(ratio * 0.25f)
            )

            if (i == CUSTOM_IDX) {
                val alpha = if (isCenter) 1f else 0.5f
                // Fill: custom hue if set, white otherwise
                val circleFill = if (hasCustomSelection)
                    Color.hsv(currentHue, 0.359f, 0.961f).copy(alpha = alpha)
                else
                    Color.White.copy(alpha = alpha)
                val circleStroke = if (hasCustomSelection)
                    Color.hsv(currentHue, 0.661f, 0.961f).copy(alpha = alpha)
                else
                    Color(0xFFCCCCCC.toInt()).copy(alpha = alpha)
                drawCircle(circleFill,   radius = circleRadius, center = Offset(slotCx, centerY))
                drawCircle(circleStroke, radius = circleRadius, center = Offset(slotCx, centerY),
                    style = Stroke(ratio * 0.18f))
                // "C" arc — black on white, white on colored
                val cColor = if (hasCustomSelection) Color.White.copy(alpha = alpha)
                             else Color.Black.copy(alpha = alpha)
                val arcR = circleRadius * 0.65f
                drawArc(
                    color      = cColor,
                    startAngle = 300f, sweepAngle = 300f, useCenter = false,
                    topLeft    = Offset(slotCx - arcR, centerY - arcR),
                    size       = Size(arcR * 2f, arcR * 2f),
                    style      = Stroke(ratio * 0.22f, cap = StrokeCap.Round)
                )
                if (isCenter) {
                    drawCircle(Color.White.copy(alpha = 0.9f),
                        radius = circleRadius + ratio * 0.2f,
                        center = Offset(slotCx, centerY),
                        style  = Stroke(ratio * 0.22f))
                }
            } else {
                val preset   = PRESETS[i] ?: continue
                val unlocked = isUnlocked(i)
                val alpha    = if (unlocked) 1f else 0.35f
                drawCircle(Color.hsv(preset.hue, 0.359f, 0.961f).copy(alpha = alpha),
                    radius = circleRadius, center = Offset(slotCx, centerY))
                drawCircle(Color.hsv(preset.hue, 0.661f, 0.961f).copy(alpha = alpha),
                    radius = circleRadius, center = Offset(slotCx, centerY),
                    style = Stroke(ratio * 0.18f))

                if (isCenter && abs(preset.hue - currentHue) < 1f) {
                    drawCircle(Color.White, radius = circleRadius + ratio * 0.2f,
                        center = Offset(slotCx, centerY), style = Stroke(ratio * 0.22f))
                }
                if (!unlocked) drawCarouselLock(slotCx, centerY, circleRadius * 0.55f, ratio)
            }
        }
    }

    private fun DrawScope.drawCarouselLock(lx: Float, ly: Float, size: Float, ratio: Float) {
        val lockColor = Color.Black.copy(alpha = 0.82f)
        val bodyW     = size * 1.3f
        val bodyH     = size * 0.95f
        val bodyTop   = ly - bodyH * 0.15f
        drawRoundRect(lockColor, topLeft = Offset(lx - bodyW / 2f, bodyTop),
            size = Size(bodyW, bodyH), cornerRadius = CornerRadius(size * 0.18f))
        val archW = bodyW * 0.58f
        val archH = size * 0.75f
        drawArc(lockColor, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(lx - archW / 2f, bodyTop - archH),
            size    = Size(archW, archH * 2f),
            style   = Stroke(ratio * 0.22f, cap = StrokeCap.Round))
    }
}
