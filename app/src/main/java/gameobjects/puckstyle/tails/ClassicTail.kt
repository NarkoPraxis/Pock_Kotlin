package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint
import utility.PaintBucket

class ClassicTail(override val theme: ColorTheme) : TailRenderer {

    private var points: MutableList<DrawablePoint>? = null

    override fun render(
        canvas: Canvas,
        puck: Puck,
        shielded: Boolean,
        launched: Boolean,
        baseFillColor: Int
    ) {
        if (points == null) {
            val length = if (shielded) 80 else 20
            points = MutableList(length) { DrawablePoint(puck.x, puck.y) }
        }
        val points = points!!

        fun ratio(i: Int) = (i.toFloat() / (points.size - 1))

        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) {
                points[i] = points[i - 1]
            } else {
                points[i] = DrawablePoint(puck)
            }

            if (shielded) points[i].setColor(PaintBucket.effectColor)
            else if (launched) points[i].setColor(puck.fillColor)
            else points[i].setColor(baseFillColor)

            val baseSize = puck.radius * 1.1f
            if (shielded) {
                points[i].size = baseSize - Settings.strokeWidth - puck.radius * ratio(i - 1)
                points[i].setAlpha((255f * (1 - ratio(i))).toInt())
            } else {
                points[i].size = baseSize - Settings.strokeWidth - puck.radius * ratio(i - 1)
            }

            points[i].drawTo(canvas)
        }
    }

    override fun clear() {
        points = null
    }
}
