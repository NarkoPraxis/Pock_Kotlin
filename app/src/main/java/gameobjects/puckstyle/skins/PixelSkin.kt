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

class PixelSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {
    private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        fill.color = colors.primary
        stroke.color = colors.secondary
        val side = renderer.radius * 1.7f
        val left = renderer.x - side / 2f
        val top = renderer.y - side / 2f
        canvas.drawRect(left, top, left + side, top + side, fill)
        stroke.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawRect(left, top, left + side, top + side, stroke)
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        PixelLaunch.spawnSquare(renderer.x, renderer.y, renderer.radius, resolvedColors().primary, theme)
    }

    override fun onShieldedCollision(position: Point) {
        PixelLaunch.spawnSquare(renderer.x, renderer.y, renderer.radius, resolvedColors().primary, theme)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(PixelCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, resolvedColors().primary, theme.main.secondary))
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
                    p.rippleSize += radius * 0.09f; p.rippleAlpha -= 12
                    if (p.rippleAlpha <= 0) p.done = true
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
                    ring.color = Palette.withAlpha(secondaryColor, p.rippleAlpha.coerceIn(0, 255))
                    canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, ring)
                }
            }
        }
    }
}
