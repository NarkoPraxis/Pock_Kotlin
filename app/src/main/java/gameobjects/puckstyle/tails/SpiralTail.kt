package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint
import utility.PaintBucket

class SpiralTail(override val theme: ColorTheme) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        if (points == null) points = MutableList(if (shielded) 80 else 20) { DrawablePoint(puck.x, puck.y) }
        val points = points!!
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1] else points[i] = DrawablePoint(puck)
            val strobe = ((i + puck.frame / 2) % 2 == 0)
            val charging = puck.currentCharge > 0
            val color = when {
                shielded -> PaintBucket.effectColor
                charging -> if (strobe) theme.primary else Palette.cyclingPurple(puck.frame)
                else -> if (strobe) theme.primary else Palette.cyclingPurple(puck.frame + 4)
            }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            points[i].setColor(color)
            points[i].size = puck.radius * 1.1f - Settings.strokeWidth - puck.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            points[i].setAlpha((255f * (1 - ratio)).toInt())
            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }
}
