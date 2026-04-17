package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.random.Random

class PlasmaTail(override val theme: ColorTheme) : TailRenderer {

    private class Pos(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Pos>? = null

    private val core = Color.WHITE
    private val mid = if (theme.isWarm) Color.rgb(255, 170, 60) else Color.rgb(120, 220, 255)
    private val edge = if (theme.isWarm) Color.rgb(255, 60, 40) else Color.rgb(40, 100, 255)

    private val dot = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val bolt = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override val zIndex: Int
        get() = 2

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        if (points == null) points = MutableList(if (renderer.shielded) 80 else 18) { Pos(renderer.x, renderer.y) }
        val points = points!!
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) { points[i].x = points[i - 1].x; points[i].y = points[i - 1].y }
            else { points[i].x = renderer.x; points[i].y = renderer.y }
        }

        val n = points.size
        for (i in 0 until n) {
            val ratio = i.toFloat() / (n - 1).coerceAtLeast(1)
            val alpha = (255f * (1 - ratio)).toInt()
            val c = when {
                ratio < 0.3f -> Palette.lerpColor(core, mid, ratio / 0.3f)
                else -> Palette.lerpColor(mid, edge, (ratio - 0.3f) / 0.7f)
            }
            dot.color = Palette.withAlpha(c, alpha)
            canvas.drawCircle(points[i].x, points[i].y, renderer.radius * (1f - ratio) * 0.8f + Settings.screenRatio * 0.06f, dot)
        }

        bolt.strokeWidth = renderer.strokePaint.strokeWidth * 0.35f
        for (i in 0 until (n - 1).coerceAtMost(10)) {
            val a = points[i]
            val b = points[i + 1]
            val ratio = i.toFloat() / n
            bolt.color = Palette.withAlpha(Color.WHITE, (200f * (1 - ratio)).toInt())
            val midX = (a.x + b.x) / 2f + (Random.nextFloat() - 0.5f) * Settings.screenRatio * 0.8f
            val midY = (a.y + b.y) / 2f + (Random.nextFloat() - 0.5f) * Settings.screenRatio * 0.8f
            canvas.drawLine(a.x, a.y, midX, midY, bolt)
            canvas.drawLine(midX, midY, b.x, b.y, bolt)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
