package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.Settings
import gameobjects.puckstyle.CachedShaderSkin
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.paddles.IceLaunch
import physics.Point
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class IceSkin(theme: ColorTheme, override val renderer: PuckRenderer) : CachedShaderSkin(theme, renderer) {

    private var lastColors = theme.main

    private val rimStroke = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // Cache to avoid recomputing rimStroke.strokeWidth every frame.
    private var cachedRadius = -1f

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(IceScoreEffect(position.x, position.y, renderer.radius, highGoal, fullCircle = false, theme))
    }

    private class IceScoreEffect(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val theme: ColorTheme
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 3f
        private val crystalPath = Path()
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val stroke = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            // strokeWidth is constant; set once at construction.
            strokeWidth = Settings.strokeWidth * 0.5f
            color = theme.main.primary
            alpha = 130
        }

        // Central large puddle
        private var centralFrame = 0
        private val centralDuration = 60
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        private class Crystal(
            var x: Float, var y: Float,
            val dirX: Float, val dirY: Float,
            val speed: Float,
            val maxDist: Float,
            val radius: Float
        ) {
            var traveled = 0f
            var postMeltFrame = -1
            var done = false
            val meltDuration = 25
            val fadeDuration = 25
            var startT = 0f
        }

        private val crystals: List<Crystal>

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)
            val fullAngles = List(12) { i -> i * (2.0 * Math.PI / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            crystals = srcAngles.map { a ->
                val adj = if (!fullCircle && !highGoal) a + Math.PI else a
                Crystal(cx, cy, cos(adj.toFloat()), sin(adj.toFloat()), maxDistance / 45f, maxDistance, radius * 0.55f)
            }
        }

        override fun step() {
            centralFrame++
            var allDone = true
            for (c in crystals) {
                if (c.done) continue
                allDone = false
                if (c.postMeltFrame < 0) {
                    c.x += c.dirX * c.speed; c.y += c.dirY * c.speed
                    c.traveled += c.speed
                    if (c.traveled >= c.maxDist) {
                        c.postMeltFrame = 0
                        c.startT = (c.traveled / (c.maxDist * 1.4f)).coerceIn(0f, 1f)
                    }
                } else {
                    c.postMeltFrame++
                    if (c.postMeltFrame >= c.meltDuration + c.fadeDuration) c.done = true
                }
            }
            if (allDone && centralFrame >= centralDuration) _isDone = true
        }

        override fun draw(canvas: Canvas) {
            val centralT = (centralFrame / centralDuration.toFloat()).coerceIn(0f, 1f)
            val centralAlpha = (100 * (1f - centralT)).toInt().coerceIn(0, 255)
            if (centralAlpha > 0) {
                fill.color = theme.main.primary
                fill.alpha = centralAlpha
                canvas.drawCircle(cx, cy, radius * 2.5f * centralT + radius * 0.5f, fill)
            }

            for (c in crystals) {
                if (c.done) continue
                if (c.postMeltFrame < 0) {
                    val progress = (c.traveled / c.maxDist).coerceIn(0f, 1f)
                    val puddleAlpha = (80 * progress).toInt().coerceIn(0, 120)
                    if (puddleAlpha > 0) {
                        fill.color = theme.main.primary; fill.alpha = puddleAlpha
                        canvas.drawCircle(c.x, c.y, c.radius * 1.5f * progress, fill)
                    }
                    drawCrystalAt(canvas, c.x, c.y, progress * 0.25f, c.radius)
                } else {
                    val crystalT = if (c.postMeltFrame < c.meltDuration)
                        c.startT + (c.postMeltFrame.toFloat() / c.meltDuration) * (1f - c.startT)
                    else 1f
                    if (crystalT < 1f) drawCrystalAt(canvas, c.x, c.y, crystalT, c.radius)
                    val growT = (c.postMeltFrame.toFloat() / (c.meltDuration + c.fadeDuration * 0.5f)).coerceIn(0f, 1f)
                    val fadeT = ((c.postMeltFrame - c.meltDuration).toFloat() / c.fadeDuration).coerceIn(0f, 1f)
                    val alpha = (120 * (1f - fadeT)).toInt().coerceIn(0, 255)
                    if (alpha > 0) {
                        fill.color = theme.main.primary; fill.alpha = alpha
                        canvas.drawCircle(c.x, c.y, c.radius * growT * 1.5f, fill)
                    }
                }
            }
        }

        private fun drawCrystalAt(canvas: Canvas, x: Float, y: Float, t: Float, r: Float) {
            val crystalR = r * (1.4f - t * 1.1f)
            if (crystalR < 1f) return
            crystalPath.reset()
            for (i in 0 until 8) {
                val angle = CRYSTAL_ANGLES[i]
                val outerR = crystalR * (if (i % 2 == 0) 2.3f else 1f)
                val px = x + cos(angle) * outerR
                val py = y + sin(angle) * outerR
                if (i == 0) crystalPath.moveTo(px, py) else crystalPath.lineTo(px, py)
            }
            crystalPath.close()
            fill.color = Color.WHITE; fill.alpha = 255
            canvas.drawPath(crystalPath, fill)
            // stroke color/alpha/strokeWidth set once at construction; no per-call reassignment.
            canvas.drawPath(crystalPath, stroke)
        }

        companion object {
            // 8-point star angles shared across all IceScoreEffect instances.
            private val CRYSTAL_ANGLES = FloatArray(8) { i -> (i * 2.0 * PI / 8).toFloat() }
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        IceLaunch.spawnImpact(position.x, position.y, renderer.radius * .4f, theme)
    }

    override fun onShieldedCollision(position: Point) {
        IceLaunch.spawnImpact(position.x, position.y, renderer.radius * .6f, theme)
    }

    override fun createShader(radius: Float): Shader {
        val midColor = Palette.lerpColor(lastColors.primary, Color.WHITE, 0.55f)
        return RadialGradient(0f, 0f, radius,
            intArrayOf(lastColors.primary, midColor, Color.WHITE),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP)
    }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        if (colors != lastColors) {
            lastColors = colors
            invalidateShader()
        }
        ensureShader(renderer.radius)

        // Update rimStroke.strokeWidth only when radius changes.
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            rimStroke.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        }

        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius, fill)
        }
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rimStroke)
    }
}
