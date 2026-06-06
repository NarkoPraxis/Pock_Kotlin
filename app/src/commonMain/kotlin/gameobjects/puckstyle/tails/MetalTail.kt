package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.pow
import shapes.DrawablePoint

class MetalTail(override val renderer: PuckRenderer) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null
    private val grey = Palette.argb(255, 140, 140, 150)
    private val metalLen = (30 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

    override fun render(scope: DrawScope) {
        // Static UI collapses to the shared list-tail density; live keeps its longer trail.
        val len = if (renderer.staticUiMode) staticPointCount else metalLen
        if (points == null || points!!.size != len) points = MutableList(len) { DrawablePoint(renderer.x, renderer.y) }
        val points = points!!
        val colors = responsiveGroup
        val lastIndex = (points.size - 1).coerceAtLeast(1)
        val useSimpleColor = renderer.isInert || renderer.shielded
        val simpleColor = if (useSimpleColor) colors.primary else 0

        if (renderer.staticUiMode) {
            for (i in points.indices) {
                val p = staticSwooshPoint(i.toFloat() / lastIndex)
                points[i].x = p.x; points[i].y = p.y
            }
        } else {
            for (i in points.size - 1 downTo 1) {
                points[i].x = points[i - 1].x
                points[i].y = points[i - 1].y
            }
            points[0].x = renderer.x
            points[0].y = renderer.y
        }

        for (i in points.indices) {
            val ratio = i.toFloat() / lastIndex
            val color = if (useSimpleColor) simpleColor else when {
                ratio < 0.5f -> Palette.lerpColor(grey, theme.main.primary, ratio * 2f)
                else -> Palette.lerpColor(theme.main.primary, Palette.WHITE, (ratio - 0.5f) * 2f)
            }
            points[i].setColor(color)
            points[i].size = renderer.radius * 0.95f
            points[i].setAlpha((255f * (1f - ratio).pow(1.5f)).toInt())
            points[i].drawTo(scope)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
