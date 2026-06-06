package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import kotlin.math.PI
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class PrismTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Frame {
        var x: Float = 0f
        var y: Float = 0f
        var angle: Float = 0f
        var radius: Float = 0f
        var osc: Float = 0f
    }

    private var history: MutableList<Frame>? = null
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

    private val cosA = FloatArray(7)
    private val sinA = FloatArray(7)

    override fun render(scope: DrawScope) {
        // Static UI collapses to the shared list-tail density; live keeps its longer trail.
        val historySize = if (renderer.staticUiMode) staticPointCount
                          else (40 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (history == null || history!!.size != historySize) {
            history = MutableList(historySize) { Frame().apply {
                x = renderer.x; y = renderer.y
                angle = renderer.frame * 0.8f
                radius = renderer.radius * .8f
                osc = 0f
            }}
        }
        val history = history!!

        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        if (renderer.staticUiMode) {
            // Static screenshot: pose facets along the swoosh; spin each by index for a frozen spiral.
            val last = (history.size - 1).coerceAtLeast(1)
            for (i in history.indices) {
                val p = staticSwooshPoint(i.toFloat() / last)
                history[i].apply { x = p.x; y = p.y; angle = i * 12f; radius = renderer.radius; this.osc = 0f }
            }
        } else {
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
        }

        val chargeRange = Settings.sweetSpotMax.toFloat() - Settings.chargeStart
        val chargeRatio = ((renderer.currentCharge - Settings.chargeStart) / chargeRange).coerceIn(0f, 1f)

        for (i in history.indices) {
            val entry = history[i]
            val ratio = i.toFloat() / (history.size - 1).coerceAtLeast(1)
            val scale = 1f - ratio
            val alpha = (200f * scale).toInt()
            if (alpha <= 0 || scale <= 0f) continue

            val r = entry.radius
            for (k in 0..6) {
                val rad = (k * 60.0 + entry.angle) * PI / 180.0
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

                val finalHue = hues[j] + entry.osc * (1f - 2f * chargeRatio)
                val color = when {
                    renderer.shielded -> {
                        val purpleCenter = if (baseHue > 180f) 290f else 270f
                        val purpleHue = purpleCenter + entry.osc * 0.5f * (1f - 2f * chargeRatio) + (j - 2.5f) * 6f
                        Palette.withAlpha(Palette.hsvThemed(purpleHue), alpha)
                    }
                    renderer.isInert -> Palette.withAlpha(hsv(hues[j], .10f, .90f), alpha)
                    else -> Palette.withAlpha(Palette.hsvThemed(finalHue), alpha)
                }
                scope.drawPath(path, Color(color))
            }
        }
    }

    override fun clear() { history = null }

    override fun fillTo(x: Float, y: Float) {
        history?.forEach { it.x = x; it.y = y }
    }
}
