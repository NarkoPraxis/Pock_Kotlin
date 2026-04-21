package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint
import utility.PaintBucket

class ClassicTail(override val theme: ColorTheme) : TailRenderer {

    private var points: MutableList<DrawablePoint>? = null

    override val zIndex: Int
        get() = 2

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val length = ((if (renderer.shielded) 80 else 20) * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != length) {
            points = MutableList(length) { DrawablePoint(renderer.x, renderer.y) }
        }
        val points = points!!

        fun ratio(i: Int) = (i.toFloat() / (points.size - 1))

        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) {
                points[i] = points[i - 1]
            } else {
                points[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            }

            if (renderer.shielded) points[i].setColor(PaintBucket.effectColor)
            else if (renderer.launched) points[i].setColor(renderer.fillColor)
            else points[i].setColor(renderer.baseFillColor)

            val baseSize = renderer.radius * 1.1f
            if (renderer.shielded) {
                points[i].size = baseSize - Settings.strokeWidth - renderer.radius * ratio(i - 1)
                points[i].setAlpha((255f * (1 - ratio(i))).toInt())
            } else {
                points[i].size = baseSize - Settings.strokeWidth - renderer.radius * ratio(i - 1)
            }

            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
