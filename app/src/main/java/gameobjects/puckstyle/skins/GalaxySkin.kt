package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.paddles.GalaxyLaunch
import physics.Point
import utility.Effects

class GalaxySkin( override val renderer: PuckRenderer) : PuckSkin {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val star = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val starPath = Path()
    private var lastRadius = -1f
    private var lastColors = theme.main

    override fun onCollisionWin(position: Point, speed: Float) {
        GalaxyLaunch.spawnStartImpact(renderer.x, renderer.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onShieldedCollision(position: Point) {
        GalaxyLaunch.spawnStartImpact(renderer.x, renderer.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(GalaxyScoreEffect(position.x, position.y, renderer.radius, theme.main.primary, theme.main.secondary, highGoal, fullCircle = false))
        GalaxyLaunch.spawnStarBurst(position.x, position.y,  renderer.radius, theme.main.primary, theme.main.secondary)
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(GalaxyScoreEffect(x, y, renderer.radius, theme.main.primary, theme.main.secondary, highGoal = true, fullCircle = true))
        GalaxyLaunch.spawnStarBurst(x, y, renderer.radius, theme.main.primary, theme.main.secondary)

    }

    private class GalaxyScoreEffect(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val colorA: Int,
        private val colorB: Int,
        highGoal: Boolean,
        fullCircle: Boolean
    ) : Effects.PersistentEffect {
        private val directions: List<Pair<Float, Float>>
        private var currentDistance = 0f
        private val maxDistance = radius * 3f
        private val speed = maxDistance / 55f
        private var frame = 0
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val path = Path()

        // Pre-computed star geometry constants (count = 5 is fixed)
        private val angleStep = (TAU / 5f)
        private val halfStep = angleStep / 2f
        private val halfPi = (Math.PI / 2.0).toFloat()

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)
            val fullAngles = List(12) { i -> i * (TAU_DOUBLE / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            directions = srcAngles.map { a ->
                val adj = if (!fullCircle && !highGoal) a + Math.PI else a
                Pair(cos(adj.toFloat()), sin(adj.toFloat()))
            }
        }

        override fun step() {
            frame++
            currentDistance += speed
            if (currentDistance >= maxDistance) _isDone = true
        }

        override fun draw(canvas: Canvas) {
            if (_isDone) return
            val life = (1f - currentDistance / maxDistance).coerceAtLeast(0f)
            val alpha = (255 * life * life).toInt().coerceIn(0, 255)
            if (alpha <= 0) return
            val color = Palette.lerpColor(colorA, colorB, 1f - life)
            val starR = radius * 0.35f * life
            if (starR < 1f) return
            paint.color = Palette.withAlpha(color, alpha)
            val rot = frame * 0.03f
            for ((dx, dy) in directions) {
                buildStar(cx + dx * currentDistance, cy + dy * currentDistance, starR, rot)
                canvas.drawPath(path, paint)
            }
        }

        private fun buildStar(scx: Float, scy: Float, outer: Float, rotation: Float) {
            val inner = outer * 0.42f
            path.reset()
            for (i in 0 until 5) {
                val outerAngle = rotation + i * angleStep - halfPi
                val tipX = scx + cos(outerAngle) * outer
                val tipY = scy + sin(outerAngle) * outer
                val prevInnerAngle = outerAngle - halfStep
                val nextInnerAngle = outerAngle + halfStep
                val prevValX = scx + cos(prevInnerAngle) * inner
                val prevValY = scy + sin(prevInnerAngle) * inner
                val nextValX = scx + cos(nextInnerAngle) * inner
                val nextValY = scy + sin(nextInnerAngle) * inner
                if (i == 0) path.moveTo(prevValX, prevValY)
                path.lineTo(tipX, tipY)
                path.lineTo(nextValX, nextValY)
            }
            path.close()
        }

        companion object {
            private val TAU = (Math.PI * 2).toFloat()
            private val TAU_DOUBLE = Math.PI * 2
        }
    }

    // FloatArray: [angularPos, distSeed, twinklePhase, angularDrift, twinkleSpeed, distFactor]
    // distFactor = distSeed * 0.72f + 0.05f — precomputed to avoid per-frame multiplication
    private val starSeeds: List<FloatArray> = List(24) {
        val distSeed = Random.nextFloat()
        floatArrayOf(
            Random.nextFloat() * TAU,
            distSeed,
            Random.nextFloat() * TAU,
            (Random.nextFloat() - 0.5f) * 0.048f,
            0.157f + Random.nextFloat() * 0.157f,
            distSeed * 0.72f + 0.05f
        )
    }

    private fun ensureShader(radius: Float) {
        if (radius == lastRadius) return
        val darkCenter = Color.argb(255, 0, 0, 0)
        val preThemeEdge = Palette.withAlpha(lastColors.primary, 130)
        val themeEdge = Palette.withAlpha(lastColors.primary, 60)
        fill.shader = RadialGradient(
            0f, 0f, radius,
            intArrayOf(darkCenter, Color.argb(160, 0, 0, 0), preThemeEdge, themeEdge, themeEdge),
            floatArrayOf(0f, 0.5f, 0.7f, 0.9f, 1f),
            Shader.TileMode.CLAMP
        )
        lastRadius = radius
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float, paint: Paint) {
        starPath.reset()
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) outerR else innerR
            val px = cx + cos(STAR_ANGLES[i]) * r
            val py = cy + sin(STAR_ANGLES[i]) * r
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    override fun drawBody(canvas: Canvas) {
        val colors = responsiveGroup
        if (colors != lastColors) {
            lastColors = colors
            lastRadius = -1f
        }
        ensureShader(renderer.radius)

        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius * 0.85f, fill)
        }

        val outerRBase = renderer.radius * 0.12f
        val primaryColor = lastColors.primary
        val frameF = renderer.frame.toFloat()
        for (seed in starSeeds) {
            seed[0] += seed[3]
            val ang = seed[0]
            val dist = seed[5] * renderer.radius
            val px = renderer.x + cos(ang) * dist
            val py = renderer.y + sin(ang) * dist
            val twinkle = (sin(frameF * seed[4] + seed[2]) + 1f) * 0.5f
            val alpha = (110 + 145 * twinkle).toInt()
            // Set color with alpha in one shot — avoids a redundant star.alpha assignment
            star.color = Palette.withAlpha(primaryColor, alpha)
            val outerR = outerRBase * (0.6f + twinkle * 0.8f)
            drawStar(canvas, px, py, outerR, outerR * 0.38f, star)
        }
    }

    companion object {
        private val TAU = (Math.PI * 2).toFloat()
        val STAR_ANGLES = FloatArray(8) { i -> (i * 45f - 90f) * Math.PI.toFloat() / 180f }
    }
}
