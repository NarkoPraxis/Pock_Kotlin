package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class PrismTail(override val theme: ColorTheme) : TailRenderer {

    private class Pos(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Pos>? = null

    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val baseHue = Palette.themeHue(theme)

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        if (points == null) points = MutableList(if (renderer.shielded) 80 else 24) { Pos(renderer.x, renderer.y) }
        val points = points!!
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        val hue = baseHue + osc
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) { points[i].x = points[i - 1].x; points[i].y = points[i - 1].y }
            else { points[i].x = renderer.x; points[i].y = renderer.y }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val size = renderer.radius * 0.6f * (1f - ratio)
            if (size <= 0f) continue
            val alpha = (200f * (1f - ratio)).toInt()
            paint.color = Palette.withAlpha(Palette.hsvThemed(hue), alpha)
            canvas.drawCircle(points[i].x, points[i].y, size, paint)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
