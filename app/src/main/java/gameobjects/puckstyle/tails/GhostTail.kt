package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class GhostTail(override val theme: ColorTheme) : TailRenderer {

    private data class Ghost(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Ghost>? = null

    private val whitePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override val zIndex: Int
        get() = 2

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val ghostLen = (30 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != ghostLen) points = MutableList(ghostLen) { Ghost(renderer.x, renderer.y) }
        val points = points!!
        val colors = resolvedColors(renderer)
        val glowColor = when {
            renderer.isInert -> colors.primary
            renderer.currentCharge >= Settings.chargeStart || renderer.shielded -> theme.effect.primary
            else -> colors.primary
        }

        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1].copy()
            else { points[i].x = renderer.x; points[i].y = renderer.y }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val size = renderer.radius * 1f - Settings.strokeWidth - renderer.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            val alpha = (255f * (1 - ratio)).toInt()
            // Outer aura ring drawn first so the white fill sits on top cleanly
            glowPaint.color = Palette.withAlpha(glowColor, (alpha * 0.45f).toInt())
            glowPaint.strokeWidth = renderer.strokePaint.strokeWidth * 1.2f
            canvas.drawCircle(points[i].x, points[i].y, size * 1.15f, glowPaint)
        }

        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1].copy()
            else { points[i].x = renderer.x; points[i].y = renderer.y }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val size = renderer.radius * 1.1f - Settings.strokeWidth - renderer.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            val alpha = (255f * (1 - ratio)).toInt()
            // White fill disc
            whitePaint.color = Color.argb((alpha * 0.75f).toInt(), 255, 255, 255)
            canvas.drawCircle(points[i].x, points[i].y, size, whitePaint)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
