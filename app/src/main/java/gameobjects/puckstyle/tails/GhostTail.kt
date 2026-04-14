package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer
import physics.Point
import utility.PaintBucket

class GhostTail(override val theme: ColorTheme) : TailRenderer {

    private data class Ghost(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Ghost>? = null

    private val whitePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        if (points == null) points = MutableList(if (shielded) 80 else 20) { Ghost(puck.x, puck.y) }
        val points = points!!
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1].copy() else { points[i].x = puck.x; points[i].y = puck.y }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val size = puck.radius * 1.1f - Settings.strokeWidth - puck.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            val alpha = (255f * (1 - ratio)).toInt()
            val glowColor = when {
                shielded -> PaintBucket.effectColor
                puck.currentCharge > 0 -> Palette.cyclingPurple(puck.frame)
                else -> theme.primary
            }
            glowPaint.color = Palette.withAlpha(glowColor, (alpha * 0.6f).toInt())
            canvas.drawCircle(points[i].x, points[i].y, size * 1.3f, glowPaint)
            whitePaint.color = Color.argb((alpha * 0.8f).toInt(), 255, 255, 255)
            canvas.drawCircle(points[i].x, points[i].y, size, whitePaint)
        }
    }

    override fun clear() { points = null }
}
