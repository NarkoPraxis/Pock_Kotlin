package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint
import utility.PaintBucket

class SpiralTail(override val theme: ColorTheme) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        if (points == null) points = MutableList(if (renderer.shielded) 80 else 20) { DrawablePoint(renderer.x, renderer.y) }
        val points = points!!
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1]
            else points[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            val strobe = ((i + renderer.frame / 2) % 2 == 0)
            val charging = renderer.currentCharge > 0
            val color = when {
                renderer.shielded -> PaintBucket.effectColor
                charging -> if (strobe) theme.primary else Palette.cyclingPurple(renderer.frame)
                else -> if (strobe) theme.primary else Palette.cyclingPurple(renderer.frame + 4)
            }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            points[i].setColor(color)
            points[i].size = renderer.radius * 1.1f - Settings.strokeWidth - renderer.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            points[i].setAlpha((255f * (1 - ratio)).toInt())
            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
