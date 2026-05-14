package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

object Palette {
    private val hsvBuffer = FloatArray(3)

    fun hsv(h: Float, s: Float, v: Float): Int {
        val hNorm = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - kotlin.math.abs(((hNorm / 60f) % 2f) - 1f))
        val m = v - c
        val r1: Float; val g1: Float; val b1: Float
        when {
            hNorm < 60f  -> { r1 = c;  g1 = x;  b1 = 0f }
            hNorm < 120f -> { r1 = x;  g1 = c;  b1 = 0f }
            hNorm < 180f -> { r1 = 0f; g1 = c;  b1 = x  }
            hNorm < 240f -> { r1 = 0f; g1 = x;  b1 = c  }
            hNorm < 300f -> { r1 = x;  g1 = 0f; b1 = c  }
            else         -> { r1 = c;  g1 = 0f; b1 = x  }
        }
        val r = ((r1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun cyclingHue(frame: Int, speed: Float = 4f): Int =
        hsv(frame * speed, THEME_SATURATION, THEME_VALUE)

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return argb(
            (alpha(a) + (alpha(b) - alpha(a)) * tt).toInt(),
            (red(a)   + (red(b)   - red(a))   * tt).toInt(),
            (green(a) + (green(b) - green(a)) * tt).toInt(),
            (blue(a)  + (blue(b)  - blue(a))  * tt).toInt()
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        argb(alpha.coerceIn(0, 255), red(color), green(color), blue(color))

    // --- Color component helpers (replaces android.graphics.Color statics) ---

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255)   shl 16) or
        (green.coerceIn(0, 255) shl 8)  or
        blue.coerceIn(0, 255)

    fun alpha(color: Int): Int = (color shr 24) and 0xFF
    fun red(color: Int): Int   = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8)  and 0xFF
    fun blue(color: Int): Int  =  color          and 0xFF

    // Common constant colors
    val WHITE: Int = 0xFFFFFFFF.toInt()
    val BLACK: Int = 0xFF000000.toInt()
    val DKGRAY: Int = 0xFF444444.toInt()
    val TRANSPARENT: Int = 0x00000000

    // --- Theme-calibrated HSV constants ---

    const val THEME_SATURATION = 0.35f
    const val HIGHLIGHT_SATURATION = 0.66f
    const val THEME_VALUE = 0.96f

    fun colorToHSV(color: Int, hsv: FloatArray) {
        val r = red(color)   / 255f
        val g = green(color) / 255f
        val b = blue(color)  / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        hsv[2] = max
        hsv[1] = if (max == 0f) 0f else delta / max
        hsv[0] = if (delta == 0f) 0f else when (max) {
            r    -> 60f * (((g - b) / delta) % 6f)
            g    -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (hsv[0] < 0f) hsv[0] += 360f
    }

    fun themeHue(theme: ColorTheme): Float {
        colorToHSV(theme.main.primary, hsvBuffer)
        return hsvBuffer[0]
    }

    fun colorHue(color: Int): Float {
        colorToHSV(color, hsvBuffer)
        return hsvBuffer[0]
    }

    fun hsvThemed(hue: Float): Int = hsv(hue, THEME_SATURATION, THEME_VALUE)
    fun hsvHighlight(hue: Float): Int = hsv(hue, HIGHLIGHT_SATURATION, THEME_VALUE)

    fun DrawScope.drawGlowRings(
        cx: Float, cy: Float,
        radius: Float,
        baseStrokeWidth: Float,
        color: Int
    ) {
        val center = Offset(cx, cy)
        drawCircle(Color(withAlpha(color, 60)),  radius, center, style = Stroke(width = baseStrokeWidth * 3.0f))
        drawCircle(Color(withAlpha(color, 130)), radius, center, style = Stroke(width = baseStrokeWidth * 1.8f))
        drawCircle(Color(color),                 radius, center, style = Stroke(width = baseStrokeWidth))
    }
}
