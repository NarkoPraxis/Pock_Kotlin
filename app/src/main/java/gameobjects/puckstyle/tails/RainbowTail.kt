package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint
import utility.PaintBucket

class RainbowTail(override val theme: ColorTheme) : TailRenderer {
    private var points: MutableList<DrawablePoint> = MutableList(Settings.tailLength) { DrawablePoint() }
    private val hueOffset = if (theme.isWarm) 0f else 200f

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        if (points.size == 0) points = MutableList(if (shielded) 80 else 20) { DrawablePoint() }
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1] else points[i] = DrawablePoint(puck)
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val color = when {
                shielded -> PaintBucket.effectColor
                puck.currentCharge > 0 -> Palette.cyclingPurple(puck.frame + i * 2)
                else -> Palette.hsv(puck.frame * 4f + hueOffset - i * 15f, 1f, 1f)
            }
            points[i].setColor(color)
            points[i].size = puck.radius * 1.1f - Settings.strokeWidth - puck.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            points[i].setAlpha((255f * (1 - ratio)).toInt())
            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points.clear() }
}
