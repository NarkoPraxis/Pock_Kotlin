package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import utility.PaintBucket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import gameobjects.puckstyle.paddles.GalaxyLaunch
import physics.Point
import utility.Effects

class GalaxySkin(override val renderer: PuckRenderer) : PuckSkin {

    private val starPath = Path()
    private var lastRadius = -1f
    private var lastColors = theme.main
    private var galaxyBrush: Brush? = null

    override fun onCollisionWin(position: Point, speed: Float) {
        GalaxyLaunch.spawnStartImpact(renderer.x, renderer.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onShieldedCollision(position: Point) {
        GalaxyLaunch.spawnStartImpact(renderer.x, renderer.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(GalaxyScoreEffect(position.x, position.y, renderer.radius, renderer.bakedPrimary(theme.main.primary), renderer.bakedSecondary(theme.main.secondary), highGoal, fullCircle = false))
        GalaxyLaunch.spawnStarBurst(position.x, position.y, renderer.radius, renderer.bakedPrimary(theme.main.primary), renderer.bakedSecondary(theme.main.secondary))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(GalaxyScoreEffect(x, y, renderer.radius, renderer.bakedPrimary(theme.main.primary), renderer.bakedSecondary(theme.main.secondary), highGoal = true, fullCircle = true))
        GalaxyLaunch.spawnStarBurst(x, y, renderer.radius, renderer.bakedPrimary(theme.main.primary), renderer.bakedSecondary(theme.main.secondary))
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

        private val path = Path()

        private val angleStep = (TAU / 5f)
        private val halfStep = angleStep / 2f
        private val halfPi = (PI / 2.0).toFloat()

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, PI)
            val fullAngles = List(12) { i -> i * (TAU_DOUBLE / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            directions = srcAngles.map { a ->
                val adj = if (!fullCircle && !highGoal) a + PI else a
                Pair(cos(adj.toFloat()), sin(adj.toFloat()))
            }
        }

        override fun step() {
            frame++
            currentDistance += speed
            if (currentDistance >= maxDistance) _isDone = true
        }

        override fun draw(scope: DrawScope) {
            if (_isDone) return
            val life = (1f - currentDistance / maxDistance).coerceAtLeast(0f)
            val alpha = (255 * life * life).toInt().coerceIn(0, 255)
            if (alpha <= 0) return
            val color = Palette.lerpColor(colorA, colorB, 1f - life)
            val starR = radius * 0.35f * life
            if (starR < 1f) return
            val drawColor = Color(Palette.withAlpha(color, alpha))
            val rot = frame * 0.03f
            for ((dx, dy) in directions) {
                buildStar(cx + dx * currentDistance, cy + dy * currentDistance, starR, rot)
                scope.drawPath(path, drawColor)
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
            private val TAU = (PI * 2).toFloat()
            private val TAU_DOUBLE = PI * 2
        }
    }

    // FloatArray: [angularPos, distSeed, twinklePhase, angularDrift, twinkleSpeed, distFactor]
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

    private fun ensureGalaxyBrush(radius: Float) {
        if (radius == lastRadius) return
        val darkCenter = PaintBucket.black
        val darkMid = Color(Palette.withAlpha(Palette.argb(255, 0, 0, 0), 160))
        val preThemeEdge = Color(Palette.withAlpha(lastColors.primary, 130))
        val themeEdge = Color(Palette.withAlpha(lastColors.primary, 60))
        galaxyBrush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to darkCenter,
                0.5f to darkMid,
                0.7f to preThemeEdge,
                0.9f to themeEdge,
                1f to themeEdge
            ),
            center = Offset.Zero,
            radius = radius
        )
        lastRadius = radius
    }

    private fun DrawScope.drawStar(cx: Float, cy: Float, outerR: Float, innerR: Float, color: Color) {
        starPath.reset()
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) outerR else innerR
            val px = cx + cos(STAR_ANGLES[i]) * r
            val py = cy + sin(STAR_ANGLES[i]) * r
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        drawPath(starPath, color)
    }

    override fun DrawScope.drawBody() {
        val colors = responsiveGroup
        if (colors != lastColors) {
            lastColors = colors
            lastRadius = -1f
        }
        ensureGalaxyBrush(renderer.radius)

        withTransform({ translate(renderer.x, renderer.y) }) {
            drawCircle(brush = galaxyBrush!!, radius = renderer.radius * 0.85f, center = Offset.Zero)
        }

        val outerRBase = renderer.radius * 0.12f
        val primaryColor = lastColors.primary
        // Strobe (not frame) so stars keep twinkling and drifting in a static UI preview; in live
        // play strobe == frame, so gameplay rendering is unchanged.
        val frameF = renderer.strobe.toFloat()
        for (seed in starSeeds) {
            seed[0] += seed[3]
            val ang = seed[0]
            val dist = seed[5] * renderer.radius
            val px = renderer.x + cos(ang) * dist
            val py = renderer.y + sin(ang) * dist
            val twinkle = (sin(frameF * seed[4] + seed[2]) + 1f) * 0.5f
            val alpha = (110 + 145 * twinkle).toInt()
            val starColor = Color(Palette.withAlpha(primaryColor, alpha))
            val outerR = outerRBase * (0.6f + twinkle * 0.8f)
            drawStar(px, py, outerR, outerR * 0.38f, starColor)
        }
    }

    companion object {
        private val TAU = (PI * 2).toFloat()
        val STAR_ANGLES = FloatArray(8) { i -> (i * 45f - 90f) * PI.toFloat() / 180f }
    }
}
