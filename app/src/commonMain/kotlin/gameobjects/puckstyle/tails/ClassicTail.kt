package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint

class ClassicTail(override val renderer: PuckRenderer) : TailRenderer {

    private var points: MutableList<DrawablePoint>? = null

    override val zIndex: Int
        get() = 2

    override fun render(scope: DrawScope) {
        val length = (20 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != length) {
            points = MutableList(length) { DrawablePoint(renderer.x, renderer.y) }
        }
        val points = points!!
        val colors = responsiveGroup

        // Inline ratio math (no capturing local fun -> no per-frame closure alloc).
        // Denominator matches the original `ratio` helper exactly: (points.size - 1).
        val ratioDenom = (points.size - 1).toFloat()
        val lastIndex = (points.size - 1).coerceAtLeast(1)
        val baseSize = renderer.radius * 1.1f
        for (i in points.size - 1 downTo 0) {
            if (renderer.staticUiMode) {
                val p = staticSwooshPoint(i.toFloat() / lastIndex)
                points[i] = DrawablePoint(p.x, p.y, renderer.strokeColor)
            } else if (i - 1 >= 0) {
                points[i] = points[i - 1]
            } else {
                points[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            }

            points[i].setColor(colors.primary)
            points[i].size = baseSize - Settings.strokeWidth - renderer.radius * ((i - 1).toFloat() / ratioDenom)
            points[i].setAlpha((255f * (1 - i.toFloat() / ratioDenom)).toInt())
            points[i].drawTo(scope)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        val pts = points ?: return
        for (i in pts.indices) {
            pts[i].x = x
            pts[i].y = y
        }
    }
}
