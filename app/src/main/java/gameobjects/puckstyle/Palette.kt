package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object Palette {
    fun hsv(h: Float, s: Float, v: Float): Int =
        Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v))

    fun cyclingHue(frame: Int, speed: Float = 4f): Int =
        hsv(frame * speed, 1f, 1f)

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * tt).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    // --- Theme-calibrated HSV constants ---

    /** Saturation of theme primary colors (#f59da0, #9dd4f5 ≈ 0.35). */
    const val THEME_SATURATION = 0.35f

    /** Saturation of theme secondary/highlight colors (#f25252, #52b6f2 ≈ 0.66). */
    const val HIGHLIGHT_SATURATION = 0.66f

    /** Shared value (brightness) for both levels ≈ 0.96. */
    const val THEME_VALUE = 0.96f

    /**
     * Extracts the hue (0–360°) from a ColorTheme's primary color.
     * Use this to anchor Rainbow/Prism/Galaxy start hues to the theme.
     *
     * Warm → ~357°   Cold → ~204°
     */
    fun themeHue(theme: ColorTheme): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(theme.main.primary, hsv)
        return hsv[0]
    }

    /**
     * Returns an HSV color at theme-primary saturation and value.
     * Use for ball fill/body cycling (Rainbow, Prism, Galaxy stars).
     */
    fun hsvThemed(hue: Float): Int = hsv(hue, THEME_SATURATION, THEME_VALUE)

    /**
     * Returns an HSV color at highlight saturation and value.
     * Use for borders and highlight strokes only.
     */
    fun hsvHighlight(hue: Float): Int = hsv(hue, HIGHLIGHT_SATURATION, THEME_VALUE)

    /**
     * Draws the standard 3-layer soft glow ring pattern used by Neon and Ghost skins.
     *
     * Draws three concentric STROKE circles at [radius]:
     *   outer ring — 60 alpha, strokeWidth * 3.0f
     *   mid ring   — 130 alpha, strokeWidth * 1.8f
     *   inner ring — full alpha, strokeWidth * 1.0f
     *
     * @param baseStrokeWidth  renderer.strokePaint.strokeWidth
     * @param paint  a reusable Paint pre-configured as isAntiAlias=true, isDither=true, style=STROKE
     */
    fun drawGlowRings(
        canvas: Canvas,
        cx: Float, cy: Float,
        radius: Float,
        baseStrokeWidth: Float,
        color: Int,
        paint: Paint
    ) {
        paint.color = withAlpha(color, 60)
        paint.strokeWidth = baseStrokeWidth * 3.0f
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = withAlpha(color, 130)
        paint.strokeWidth = baseStrokeWidth * 1.8f
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = color
        paint.strokeWidth = baseStrokeWidth
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
