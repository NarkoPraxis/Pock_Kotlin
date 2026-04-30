package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.pow
import shapes.DrawablePoint

class MetalTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null
    private val grey = Color.rgb(140, 140, 150)
    private val metalLen = (30 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

    override fun render(canvas: Canvas) {
        if (points == null || points!!.size != metalLen) points = MutableList(metalLen) { DrawablePoint(renderer.x, renderer.y) }
        val points = points!!
        val colors = resolvedColors()
        val lastIndex = (points.size - 1).coerceAtLeast(1)
        val useSimpleColor = renderer.isInert || renderer.shielded
        val simpleColor = if (useSimpleColor) colors.primary else 0

        // Shift positions: copy each slot from the one ahead of it (tail-to-head order),
        // then update head slot in place — zero allocations.
        for (i in points.size - 1 downTo 1) {
            points[i].x = points[i - 1].x
            points[i].y = points[i - 1].y
        }
        points[0].x = renderer.x
        points[0].y = renderer.y

        for (i in points.indices) {
            val ratio = i.toFloat() / lastIndex
            val color = if (useSimpleColor) simpleColor else when {
                ratio < 0.5f -> Palette.lerpColor(grey, theme.main.primary, ratio * 2f)
                else -> Palette.lerpColor(theme.main.primary, Color.WHITE, (ratio - 0.5f) * 2f)
            }
            points[i].setColor(color)
            points[i].size = renderer.radius * 0.95f
            points[i].setAlpha((255f * (1f - ratio).pow(1.5f)).toInt())
            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
