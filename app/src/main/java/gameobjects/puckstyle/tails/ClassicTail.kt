package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint

class ClassicTail( override val renderer: PuckRenderer) : TailRenderer {

    private var points: MutableList<DrawablePoint>? = null

    override val zIndex: Int
        get() = 2

    override fun render(canvas: Canvas) {
        val length = (20 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != length) {
            points = MutableList(length) { DrawablePoint(renderer.x, renderer.y) }
        }
        val points = points!!
        val colors = responsiveGroup

        fun ratio(i: Int) = (i.toFloat() / (points.size - 1))

        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) {
                points[i] = points[i - 1]
            } else {
                points[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            }

            points[i].setColor(colors.primary)
            val baseSize = renderer.radius * 1.1f
            points[i].size = baseSize - Settings.strokeWidth - renderer.radius * ratio(i - 1)
            points[i].setAlpha((255f * (1 - ratio(i))).toInt())
            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
