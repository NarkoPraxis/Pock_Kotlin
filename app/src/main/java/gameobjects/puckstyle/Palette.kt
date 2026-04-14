package gameobjects.puckstyle

import android.graphics.Color

object Palette {
    private val saturatedPurples = intArrayOf(
        Color.rgb(180, 0, 255),
        Color.rgb(140, 0, 220),
        Color.rgb(220, 60, 255),
        Color.rgb(100, 0, 180)
    )

    fun cyclingPurple(frame: Int): Int =
        saturatedPurples[((frame / 3) % saturatedPurples.size + saturatedPurples.size) % saturatedPurples.size]

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
}
