package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.PixelLaunch
import physics.Point
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PixelSkin(override val renderer: PuckRenderer) : PuckSkin {

    // Cached radius-derived value
    private var cachedRadius = -1f
    private var cachedSide = 0f
    // Stroke is a heap class — cache it; strokeWidth is effectively immutable after setup
    private var cachedStrokeW = -1f
    private var cachedStroke = Stroke(width = 0f)

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            cachedSide = renderer.radius * .85f
        }
        if (cachedStrokeW != renderer.strokeWidth) {
            cachedStrokeW = renderer.strokeWidth
            cachedStroke = Stroke(width = renderer.strokeWidth)
        }
    }

    override fun DrawScope.drawBody() {
        ensureCache()
        val left = renderer.x - cachedSide
        val top = renderer.y - cachedSide
        val size = Size(2 * cachedSide, 2 * cachedSide)
        drawRect(Color(responsivePrimary), topLeft = Offset(left, top), size = size)
        drawRect(
            Color(responsiveSecondary),
            topLeft = Offset(left, top),
            size = size,
            style = cachedStroke
        )
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        PixelLaunch.spawnSquare(renderer.x, renderer.y, renderer.radius, responsivePrimary, renderer.bakedSecondary(theme.main.secondary))
    }

    override fun onShieldedCollision(position: Point) {
        PixelLaunch.spawnSquare(renderer.x, renderer.y, renderer.radius, responsivePrimary, renderer.bakedSecondary(theme.main.secondary))
    }

    override val explosionFrequency get() = 25
    override val scatterDensity get() = 0.8f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(PixelCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, responsivePrimary, renderer.bakedSecondary(theme.main.secondary)))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(PixelCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, responsivePrimary, renderer.bakedSecondary(theme.main.secondary)))
    }

    private class PixelCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val color: Int,
        private val secondaryColor: Int
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 5f
        private val halfSide = radius * 0.4f
        private val ringStrokeWidth = radius * 0.3f
        // Stroke is a heap class; width is constant for this effect's lifetime — build once
        private val ringStroke = Stroke(width = ringStrokeWidth)

        private class Pixel(
            var x: Float, var y: Float,
            val dirX: Float, val dirY: Float,
            val speed: Float,
            val maxDist: Float,
            val halfSide: Float
        ) {
            var traveled = 0f
            var rippling = false
            var rippleSize = 0f
            var rippleAlpha = 0
            var done = false
        }

        // Array (not List): index iteration in step()/draw() is allocation-free
        private val pixels: Array<Pixel>
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        init {
            val baseAngles = doubleArrayOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, PI)
            val srcAngles = if (fullCircle) DoubleArray(12) { i -> i * (2.0 * PI / 12) } else baseAngles
            pixels = Array(srcAngles.size) { idx ->
                val a = srcAngles[idx]
                val adj = if (!fullCircle && !highGoal) a + PI else a
                Pixel(cx, cy, cos(adj.toFloat()), sin(adj.toFloat()), maxDistance / 50f, maxDistance, halfSide)
            }
        }

        override fun step() {
            var allDone = true
            for (i in pixels.indices) {
                val p = pixels[i]
                if (p.done) continue
                allDone = false
                if (!p.rippling) {
                    p.x += p.dirX * p.speed; p.y += p.dirY * p.speed
                    p.traveled += p.speed
                    if (p.traveled >= p.maxDist) {
                        p.rippling = true; p.rippleSize = p.halfSide * 1.8f; p.rippleAlpha = 200
                    }
                } else {
                    p.rippleSize += radius * 0.15f
                    p.rippleAlpha -= 12
                    if (p.rippleAlpha <= 0) {
                        p.rippleAlpha = 0
                        p.done = true
                    }
                }
            }
            if (allDone) _isDone = true
        }

        override fun draw(scope: DrawScope) {
            // index loop over Array avoids per-frame Iterator allocation
            for (i in pixels.indices) {
                val p = pixels[i]
                if (p.done) continue
                if (!p.rippling) {
                    scope.drawRect(
                        Color(secondaryColor),
                        topLeft = Offset(p.x - p.halfSide, p.y - p.halfSide),
                        size = Size(p.halfSide * 2f, p.halfSide * 2f),
                        style = ringStroke
                    )
                } else {
                    val half = p.rippleSize / 2f
                    scope.drawRect(
                        Color(Palette.withAlpha(secondaryColor, p.rippleAlpha)),
                        topLeft = Offset(p.x - half, p.y - half),
                        size = Size(half * 2f, half * 2f),
                        style = ringStroke
                    )
                }
            }
        }
    }
}
