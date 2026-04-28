package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class PrismTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {

    private class Frame {
        var x: Float = 0f
        var y: Float = 0f
        var angle: Float = 0f
        var radius: Float = 0f
        var osc: Float = 0f
    }

    private var history: MutableList<Frame>? = null
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val path = Path()

    override val zIndex: Int
        get() = 1
    private val baseHue = Palette.themeHue(theme)
    private val shieldHue = Palette.colorHue(theme.shield.primary)
    private val inertHue = Palette.colorHue(theme.inert.primary)
    private val hues = floatArrayOf(
        baseHue,
        baseHue + 40f,
        baseHue - 30f,
        baseHue + 20f,
        baseHue + 60f,
        baseHue - 15f
    )

    private val baseShieldHues = floatArrayOf(
        shieldHue,
        shieldHue + 40f,
        shieldHue - 30f,
        shieldHue + 20f,
        shieldHue + 60f,
        shieldHue - 15f
    )

    private val baseInertHues = floatArrayOf(
        inertHue,
        inertHue + 40f,
        inertHue - 30f,
        inertHue + 20f,
        inertHue + 60f,
        inertHue - 15f
    )

    // Reusable trig buffers — 7 boundary angles per hexagon (0°, 60°, ..., 360°)
    private val cosA = FloatArray(7)
    private val sinA = FloatArray(7)

    override fun render(canvas: Canvas) {
        val historySize = (40 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (history == null || history!!.size != historySize) {
            history = MutableList(historySize) { Frame().apply {
                x = renderer.x; y = renderer.y
                angle = renderer.frame * 0.8f
                radius = renderer.radius * .8f
                osc = 0f
            }}
        }
        val history = history!!

        // Shift history by value-copy, then write current frame into [0]
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        for (i in history.size - 1 downTo 1) {
            history[i].x = history[i - 1].x
            history[i].y = history[i - 1].y
            history[i].angle = history[i - 1].angle
            history[i].radius = history[i - 1].radius
            history[i].osc = history[i - 1].osc
        }
        history[0].apply {
            x = renderer.x; y = renderer.y
            angle = renderer.frame * 0.8f
            radius = renderer.radius
            this.osc = osc
        }

        val chargeRange = Settings.sweetSpotMax.toFloat() - Settings.chargeStart
        val chargeRatio = ((renderer.currentCharge - Settings.chargeStart) / chargeRange).coerceIn(0f, 1f)

        for (i in history.indices) {
            val entry = history[i]
            val ratio = i.toFloat() / (history.size - 1).coerceAtLeast(1)
            val scale = 1f - ratio
            val alpha = (200f * scale).toInt()
            if (alpha <= 0 || scale <= 0f) continue

            // Precompute 7 boundary angles for this snapshot
            val r = entry.radius
            for (k in 0..6) {
                val rad = (k * 60.0 + entry.angle) * Math.PI / 180.0
                cosA[k] = kotlin.math.cos(rad).toFloat()
                sinA[k] = kotlin.math.sin(rad).toFloat()
            }

            for (j in 0 until 6) {
                val p0x = entry.x
                val p0y = entry.y
                val p1x = entry.x + cosA[j] * r
                val p1y = entry.y + sinA[j] * r
                val p2x = entry.x + cosA[j + 1] * r
                val p2y = entry.y + sinA[j + 1] * r

                val centX = (p0x + p1x + p2x) / 3f
                val centY = (p0y + p1y + p2y) / 3f

                path.reset()
                path.moveTo(centX + (p0x - centX) * scale, centY + (p0y - centY) * scale)
                path.lineTo(centX + (p1x - centX) * scale, centY + (p1y - centY) * scale)
                path.lineTo(centX + (p2x - centX) * scale, centY + (p2y - centY) * scale)
                path.close()

                // Charge inverts the osc shift: at full charge, storedOsc is negated
                val finalHue = hues[j] + entry.osc * (1f - 2f * chargeRatio)
                paint.color = Palette.withAlpha(Palette.hsvThemed(finalHue), alpha)
                if (renderer.shielded) {
                    val purpleCenter = if (baseHue > 180f) 290f else 270f
                    val purpleHue = purpleCenter + entry.osc * 0.5f * (1f - 2f * chargeRatio) + (j - 2.5f) * 6f
                    paint.color = Palette.withAlpha(Palette.hsvThemed(purpleHue), alpha)
                } else if (renderer.isInert) {
                    paint.color = Palette.withAlpha(hsv( hues[j], .10f, .90f), alpha)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    override fun clear() { history = null }

    override fun fillTo(x: Float, y: Float) {
        history?.forEach { it.x = x; it.y = y }
    }
}
