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
import utility.PaintBucket

class MetalTail(override val theme: ColorTheme) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null
    private val grey = Color.rgb(140, 140, 150)

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val metalLen = ((if (renderer.shielded) 80 else 12) * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != metalLen) points = MutableList(metalLen) { DrawablePoint(renderer.x, renderer.y) }
        val points = points!!
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1]
            else points[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val color = when {
                renderer.shielded -> PaintBucket.effectColor
                ratio < 0.5f -> Palette.lerpColor(grey, theme.primary, ratio * 2f)
                else -> Palette.lerpColor(theme.primary, Color.WHITE, (ratio - 0.5f) * 2f)
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
