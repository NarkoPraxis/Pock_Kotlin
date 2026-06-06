package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.random.Random

class PlasmaTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Pos(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Pos>? = null

    private var cachedRadius = -1f
    private var scaledRadius = 0f
    private var boltStrokeWidth = 0f
    private val screenRatioJitter = Settings.screenRatio * 0.8f
    private val dotOffset = Settings.screenRatio * 0.06f

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            scaledRadius = renderer.radius * 0.8f
            boltStrokeWidth = renderer.strokeWidth * 0.35f
        }
    }

    override val zIndex: Int
        get() = 1

    override fun render(scope: DrawScope) {
        // Static UI collapses to the shared list-tail density; live keeps its own trail length.
        val plasmaLen = if (renderer.staticUiMode) staticPointCount
                        else (18 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != plasmaLen) points = MutableList(plasmaLen) { Pos(renderer.x, renderer.y) }
        val points = points!!

        if (renderer.staticUiMode) {
            val last = (points.size - 1).coerceAtLeast(1)
            for (i in points.indices) {
                val p = staticSwooshPoint(i.toFloat() / last)
                points[i].x = p.x; points[i].y = p.y
            }
        } else {
            for (i in points.size - 1 downTo 0) {
                if (i - 1 >= 0) { points[i].x = points[i - 1].x; points[i].y = points[i - 1].y }
                else { points[i].x = renderer.x; points[i].y = renderer.y }
            }
        }

        ensureCache()

        val stateColors = responsiveGroup
        val mid = stateColors.primary
        val edge = stateColors.secondary
        val n = points.size
        val nDenomInv = 1f / (n - 1).coerceAtLeast(1)
        val white = Palette.WHITE

        for (i in 0 until n) {
            val ratio = i.toFloat() * nDenomInv
            val alpha = (255f * (1f - ratio)).toInt()
            val c = when {
                ratio < 0.3f -> Palette.lerpColor(white, mid, ratio / 0.3f)
                else -> Palette.lerpColor(mid, edge, (ratio - 0.3f) / 0.7f)
            }
            scope.drawCircle(
                color = Color(Palette.withAlpha(c, alpha)),
                radius = scaledRadius * (1f - ratio) + dotOffset,
                center = Offset(points[i].x, points[i].y)
            )
        }

        val boltMax = (n - 1).coerceAtMost(10)
        val nInv = 1f / n
        for (i in 0 until boltMax) {
            val a = points[i]
            val b = points[i + 1]
            val ratio = i.toFloat() * nInv
            val boltAlpha = (200f * (1f - ratio)).toInt()
            // Static UI freezes the bolt jitter (seeded per segment) so the screenshot doesn't flicker.
            val rnd = if (renderer.staticUiMode) Random(i + 1) else Random
            val midX = (a.x + b.x) * 0.5f + (rnd.nextFloat() - 0.5f) * screenRatioJitter
            val midY = (a.y + b.y) * 0.5f + (rnd.nextFloat() - 0.5f) * screenRatioJitter
            val boltColor = Color(Palette.withAlpha(white, boltAlpha))
            scope.drawLine(boltColor, Offset(a.x, a.y), Offset(midX, midY), boltStrokeWidth, StrokeCap.Round)
            scope.drawLine(boltColor, Offset(midX, midY), Offset(b.x, b.y), boltStrokeWidth, StrokeCap.Round)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
