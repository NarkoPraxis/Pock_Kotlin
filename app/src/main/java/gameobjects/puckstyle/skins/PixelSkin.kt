package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.PixelLaunch
import physics.Point
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PixelSkin( override val renderer: PuckRenderer) : PuckSkin {
    private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }

    // Cached radius-derived values
    private var cachedRadius = -1f
    private var cachedSide = 0f
    private var cachedLeft = 0f
    private var cachedTop = 0f

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            cachedSide = renderer.radius * .85f
        }
        stroke.strokeWidth = renderer.strokePaint.strokeWidth
        cachedLeft = renderer.x - cachedSide
        cachedTop  = renderer.y - cachedSide
    }

    override fun drawBody(canvas: Canvas) {
        ensureCache()
        fill.color   = responsivePrimary
        stroke.color = responsiveSecondary
        canvas.drawRect(cachedLeft, cachedTop, cachedLeft + 2 * cachedSide, cachedTop + 2 * cachedSide, fill)
        canvas.drawRect(cachedLeft, cachedTop, cachedLeft + 2 * cachedSide, cachedTop + 2 * cachedSide, stroke)
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        PixelLaunch.spawnSquare(renderer.x, renderer.y, renderer.radius, responsivePrimary, theme)
    }

    override fun onShieldedCollision(position: Point) {
        PixelLaunch.spawnSquare(renderer.x, renderer.y, renderer.radius, responsivePrimary, theme)
    }

    override val explosionFrequency get() = 25
    override val scatterDensity get() = 0.8f

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(PixelCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, responsivePrimary, theme.main.secondary))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(PixelCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, responsivePrimary, theme.main.secondary))
    }

    private class PixelCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val color: Int,
        private val secondaryColor: Int
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 3f
        private val halfSide = radius * 0.55f
        private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        private val ring = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = radius * 0.3f }

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
            // alpha is clamped to [0,255] in step() so draw() never needs coerceIn
            var rippleAlpha = 0
            var done = false
        }

        private val pixels: List<Pixel>
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)
            val fullAngles = List(12) { i -> i * (2.0 * Math.PI / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            pixels = srcAngles.map { a ->
                val adj = if (!fullCircle && !highGoal) a + Math.PI else a
                Pixel(cx, cy, cos(adj.toFloat()), sin(adj.toFloat()), maxDistance / 50f, maxDistance, halfSide)
            }
        }

        override fun step() {
            var allDone = true
            for (p in pixels) {
                if (p.done) continue
                allDone = false
                if (!p.rippling) {
                    p.x += p.dirX * p.speed; p.y += p.dirY * p.speed
                    p.traveled += p.speed
                    if (p.traveled >= p.maxDist) {
                        p.rippling = true; p.rippleSize = p.halfSide * 1.8f; p.rippleAlpha = 200
                    }
                } else {
                    p.rippleSize += radius * 0.09f
                    p.rippleAlpha -= 12
                    if (p.rippleAlpha <= 0) {
                        p.rippleAlpha = 0
                        p.done = true
                    }
                }
            }
            if (allDone) _isDone = true
        }

        override fun draw(canvas: Canvas) {
            for (p in pixels) {
                if (p.done) continue
                if (!p.rippling) {
                    fill.color = color
                    canvas.drawRect(p.x - p.halfSide, p.y - p.halfSide, p.x + p.halfSide, p.y + p.halfSide, fill)
                } else {
                    val half = p.rippleSize / 2f
                    // rippleAlpha is already clamped to [0,255] by step()
                    ring.color = Palette.withAlpha(secondaryColor, p.rippleAlpha)
                    canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, ring)
                }
            }
        }
    }
}
