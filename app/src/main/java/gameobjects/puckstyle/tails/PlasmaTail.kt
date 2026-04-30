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

class PlasmaTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {

    private class Pos(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Pos>? = null

    private val core = Color.WHITE

    private val dot = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val bolt = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    // Cached radius-derived values — updated when renderer.radius changes
    private var cachedRadius = -1f
    private var scaledRadius = 0f          // renderer.radius * 0.8f
    private var boltStrokeWidth = 0f      // renderer.strokePaint.strokeWidth * 0.35f
    private val screenRatioJitter = Settings.screenRatio * 0.8f   // constant after init
    private val dotOffset = Settings.screenRatio * 0.06f          // constant after init

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            scaledRadius = renderer.radius * 0.8f
            boltStrokeWidth = renderer.strokePaint.strokeWidth * 0.35f
        }
    }

    override val zIndex: Int
        get() = 1

    override fun render(canvas: Canvas) {
        val plasmaLen = (18 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != plasmaLen) points = MutableList(plasmaLen) { Pos(renderer.x, renderer.y) }
        val points = points!!

        // Shift history
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) { points[i].x = points[i - 1].x; points[i].y = points[i - 1].y }
            else { points[i].x = renderer.x; points[i].y = renderer.y }
        }

        ensureCache()

        val stateColors = resolvedColors()
        val mid = stateColors.primary
        val edge = stateColors.secondary
        val n = points.size
        val nDenomInv = 1f / (n - 1).coerceAtLeast(1)

        for (i in 0 until n) {
            val ratio = i.toFloat() * nDenomInv
            val alpha = (255f * (1f - ratio)).toInt()
            val c = when {
                ratio < 0.3f -> Palette.lerpColor(core, mid, ratio / 0.3f)
                else -> Palette.lerpColor(mid, edge, (ratio - 0.3f) / 0.7f)
            }
            dot.color = Palette.withAlpha(c, alpha)
            canvas.drawCircle(points[i].x, points[i].y, scaledRadius * (1f - ratio) + dotOffset, dot)
        }

        bolt.strokeWidth = boltStrokeWidth
        val boltMax = (n - 1).coerceAtMost(10)
        val nInv = 1f / n
        for (i in 0 until boltMax) {
            val a = points[i]
            val b = points[i + 1]
            val ratio = i.toFloat() * nInv
            bolt.color = Palette.withAlpha(Color.WHITE, (200f * (1f - ratio)).toInt())
            val midX = (a.x + b.x) * 0.5f + (Random.nextFloat() - 0.5f) * screenRatioJitter
            val midY = (a.y + b.y) * 0.5f + (Random.nextFloat() - 0.5f) * screenRatioJitter
            canvas.drawLine(a.x, a.y, midX, midY, bolt)
            canvas.drawLine(midX, midY, b.x, b.y, bolt)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
