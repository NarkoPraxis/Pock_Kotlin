package gameobjects.puckstyle.paddles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GalaxyLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val starPath = Path()

    private var cachedStrokeWidth = -1f

    override var minDist: Float = 0f
        get() = 0f

    override val alwaysVisible: Boolean = true
    override val zIndex: Int get() = -1

    private data class StarDesc(val orbitRadius: Float, val starRadius: Float)

    private var cachedStarDescsRadius = -1f
    private val starDescs = arrayOf(StarDesc(0f, 0f), StarDesc(0f, 0f), StarDesc(0f, 0f))

    private fun ensureStarDescs() {
        if (renderer.radius == cachedStarDescsRadius) return
        cachedStarDescsRadius = renderer.radius
        val r = renderer.radius
        starDescs[0] = StarDesc(r * 2f, r)
        starDescs[1] = StarDesc(r * 3f, r * 0.9f)
        starDescs[2] = StarDesc(r * 4f, r * 0.6f)
        cachedStrokeWidth = r * 0.2f
    }

    private val orbitPhaseOffset = floatArrayOf(0f, (PI / 5f).toFloat(), (2 * PI / 5f).toFloat())
    private val ORBIT_SPEED = 0.022f

    private val starReturnProgress = FloatArray(1) { 1f }
    private var lastStrikeProgress = 0f

    private fun buildStar(cx: Float, cy: Float, outer: Float, rotation: Float) {
        val inner = outer * 0.40f
        val roundness = outer * 0.04f
        starPath.reset()
        for (i in 0 until 5) {
            val outerAngle = rotation + i * ANGLE_STEP_5 - HALF_PI
            val tipX = cx + cos(outerAngle) * outer
            val tipY = cy + sin(outerAngle) * outer
            val perpX = -sin(outerAngle)
            val perpY =  cos(outerAngle)
            val prevInnerAngle = outerAngle - HALF_STEP_5
            val nextInnerAngle = outerAngle + HALF_STEP_5
            val prevValX = cx + cos(prevInnerAngle) * inner
            val prevValY = cy + sin(prevInnerAngle) * inner
            val nextValX = cx + cos(nextInnerAngle) * inner
            val nextValY = cy + sin(nextInnerAngle) * inner
            if (i == 0) starPath.moveTo(prevValX, prevValY)
            val cp1X = tipX - perpX * roundness; val cp1Y = tipY - perpY * roundness
            val cp2X = tipX + perpX * roundness; val cp2Y = tipY + perpY * roundness
            starPath.cubicTo(cp1X, cp1Y, tipX, tipY, tipX, tipY)
            starPath.cubicTo(tipX, tipY, cp2X, cp2Y, nextValX, nextValY)
        }
        starPath.close()
    }

    private fun resolveStarColors(starIndex: Int, ph: ChargePhase): Pair<Int, Int> {
        // Under a rainbow override the body follows the responsive group so the stars strobe with
        // the ball (this is what was missing — Galaxy read theme.* directly and never cycled).
        if (renderer.responsiveIsRainbow) {
            return Pair(renderer.responsiveColorGroup.secondary, renderer.responsiveColorGroup.primary)
        }
        if (renderer.shielded && phase == ChargePhase.Idle) return Pair(theme.shield.secondary, theme.shield.primary)
        if (renderer.isInert) return Pair(theme.inert.secondary, theme.inert.primary)
        if (ph == ChargePhase.Inert) return Pair(theme.inert.secondary, theme.inert.primary)
        return Pair(theme.main.secondary, theme.main.primary)
    }

    override fun drawIdlePaddle(scope: DrawScope) {
        ensureStarDescs()
        val sw = cachedStrokeWidth
        val descs = starDescs
        val sx = renderer.x; val sy = renderer.y
        // animFrame follows the strobe clock in static UI so the stars keep orbiting in place.
        val baseRot = animFrame * ORBIT_SPEED * 1.5f + orbitPhaseOffset[0]
        val (stroke1, fill1) = resolveStarColors(1, phase)

        buildStar(sx, sy, descs[0].starRadius * .8f, baseRot)
        val path1a = Path().apply { addPath(starPath) }
        buildStar(sx, sy, descs[1].starRadius, -baseRot)
        val path1b = Path().apply { addPath(starPath) }

        scope.drawPath(path1a, Color(fill1), style = Stroke(width = sw))
        scope.drawPath(path1b, Color(stroke1), style = Stroke(width = sw))
    }

    override fun drawChargingPaddle(scope: DrawScope) {
        ensureStarDescs()
        val sw = cachedStrokeWidth
        val desc = starDescs[0]
        val dist = paddleDistance.coerceIn(0f, renderer.radius * 5f)
        val sx = renderer.x - aimX * dist
        val sy = renderer.y - aimY * dist
        val rot = animFrame * ORBIT_SPEED * 1.5f + orbitPhaseOffset[0]

        val dipStart = 0.2f; val dipEnd = 0.5f
        val halfSize = renderer.radius * 0.5f
        val halfRatio = halfSize / desc.starRadius
        val starSizeRatio = when {
            phase == ChargePhase.Inert    -> halfRatio
            phase == ChargePhase.Draining -> halfRatio + ((chargeFillRatio - 0.5f) / 0.5f).coerceIn(0f, 1f) * (1f - halfRatio)
            chargeFillRatio <= dipStart   -> 1f - (chargeFillRatio / dipStart) * (1f - halfRatio)
            chargeFillRatio <= dipEnd     -> halfRatio
            else                          -> halfRatio + ((chargeFillRatio - dipEnd) / (1f - dipEnd)) * (1f - halfRatio)
        }

        val (stroke2, fill2) = resolveStarColors(2, phase)
        buildStar(sx, sy, desc.starRadius * starSizeRatio, rot)
        val outerPath = Path().apply { addPath(starPath) }
        buildStar(sx, sy, desc.starRadius * chargeFillRatio * 0.8f, rot)
        val chargePath = Path().apply { addPath(starPath) }

        scope.drawPath(outerPath, Color(fill2))
        scope.drawPath(outerPath, Color(stroke2), style = Stroke(width = sw))
        scope.drawPath(chargePath, Color(renderer.invertedChargeColor(theme.shield.primary)))
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        ensureStarDescs()
        val sw = cachedStrokeWidth
        if (progress < lastStrikeProgress) starReturnProgress[0] = 0f
        lastStrikeProgress = progress

        val desc = starDescs[0]
        val ret = starReturnProgress[0]
        val sx = cx + (renderer.x - cx) * ret
        val sy = cy + (renderer.y - cy) * ret
        val rot = animFrame * ORBIT_SPEED * 1.5f + orbitPhaseOffset[0]

        val (stroke0, fill0) = resolveStarColors(0, phase)
        buildStar(sx, sy, desc.starRadius, rot)
        val strikePath = Path().apply { addPath(starPath) }

        scope.drawPath(strikePath, Color(fill0))
        scope.drawPath(strikePath, Color(stroke0), style = Stroke(width = sw))
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(NebulaMark(rx, ry, renderer.radius,
            renderer.bakedPrimary(theme.shield.primary), renderer.bakedSecondary(theme.shield.secondary)))
    }

    companion object {
        private val STAR_ANGLES = FloatArray(8) { i -> (i * 45f - 90f) * PI.toFloat() / 180f }
        private val ANGLE_STEP_5 = (2f * PI / 5f).toFloat()
        private val HALF_STEP_5  = ANGLE_STEP_5 / 2f
        private val HALF_PI      = (PI / 2f).toFloat()

        fun spawnStartImpact(cx: Float, cy: Float, radius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(NebulaMark(cx, cy, radius, primary, secondary))
        }

        fun spawnStarBurst(cx: Float, cy: Float, radius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(NebulaMark(cx, cy, radius, primary, secondary, true))
        }
    }

    private class NebulaMark(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val colorA: Int, private val colorB: Int,
        private val noStar: Boolean = false
    ) : Effects.PersistentEffect {
        private val path = Path()
        private val burstPath = Path()
        private var frame = 0
        override val isDone = false

        private class BurstStar(
            var x: Float, var y: Float,
            var vx: Float, var vy: Float,
            var life: Float,
            val twinkleSpeed: Float, val twinkleSeedRad: Float
        )

        private val burstStars: List<BurstStar>
        private val BURST_DURATION = 48
        private val screenRatioHalf = Settings.screenRatio * 0.5f
        private val angleStep = (2f * PI / 5f).toFloat()
        private val halfStep  = angleStep / 2f
        private val halfPi    = (PI / 2f).toFloat()
        private val starStrokeWidth = radius * 0.2f

        init {
            val tau = (PI * 2).toFloat()
            val count = 22
            burstStars = List(count) { i ->
                val angle = (i.toFloat() / count) * tau + Random.nextFloat() * 0.28f
                val speed = radius * (0.09f + Random.nextFloat() * 0.13f)
                BurstStar(cx, cy, cos(angle) * speed, sin(angle) * speed, 1f,
                    0.12f + Random.nextFloat() * 0.18f, Random.nextFloat() * tau)
            }
        }

        private fun drawBurstStar(scope: DrawScope, bcx: Float, bcy: Float, outerR: Float, color: Int) {
            val innerR = outerR * 0.4f
            burstPath.reset()
            for (i in 0 until 8) {
                val r = if (i % 2 == 0) outerR else innerR
                val px = bcx + cos(STAR_ANGLES[i]) * r
                val py = bcy + sin(STAR_ANGLES[i]) * r
                if (i == 0) burstPath.moveTo(px, py) else burstPath.lineTo(px, py)
            }
            burstPath.close()
            scope.drawPath(burstPath, Color(color))
        }

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val t = (frame.toFloat() / 150f).coerceIn(0f, 1f)
            val alpha = (255 * (1f - t)).toInt().coerceIn(135, 255)

            if (frame <= BURST_DURATION) {
                val burstT = frame.toFloat() / BURST_DURATION
                val burstColor = Palette.lerpColor(colorA, colorB, burstT)
                val frameF = frame.toFloat()
                for (s in burstStars) {
                    s.x += s.vx; s.y += s.vy
                    s.vx *= 0.96f; s.vy *= 0.96f
                    s.life = (1f - burstT).coerceAtLeast(0f)
                    val twinkle = 0.75f + 0.25f * sin(frameF * s.twinkleSpeed + s.twinkleSeedRad)
                    val starAlpha = (255f * s.life * s.life).toInt().coerceIn(0, 255)
                    val outerR = screenRatioHalf * s.life * twinkle
                    if (outerR > 0.5f) drawBurstStar(scope, s.x, s.y, outerR, Palette.withAlpha(burstColor, starAlpha))
                }
            }

            if (!noStar) {
                val c = Palette.withAlpha(Palette.lerpColor(colorB, colorA, t), alpha)
                val starR = radius * (1f - t * 0.3f).coerceAtLeast(0.5f)
                buildStar(path, cx, cy, starR, frame * 0.03f)
                scope.drawPath(path, Color(c), style = Stroke(width = starStrokeWidth, cap = StrokeCap.Round))
            }
        }

        private fun buildStar(dst: Path, cx: Float, cy: Float, outer: Float, rotation: Float) {
            val inner = outer * 0.42f
            val roundness = outer * 0.14f
            dst.reset()
            for (i in 0 until 5) {
                val outerAngle = rotation + i * angleStep - halfPi
                val tipX = cx + cos(outerAngle) * outer
                val tipY = cy + sin(outerAngle) * outer
                val perpX = -sin(outerAngle); val perpY = cos(outerAngle)
                val prevInnerAngle = outerAngle - halfStep
                val nextInnerAngle = outerAngle + halfStep
                val prevValX = cx + cos(prevInnerAngle) * inner
                val prevValY = cy + sin(prevInnerAngle) * inner
                val nextValX = cx + cos(nextInnerAngle) * inner
                val nextValY = cy + sin(nextInnerAngle) * inner
                if (i == 0) dst.moveTo(prevValX, prevValY)
                val cp1X = tipX - perpX * roundness; val cp1Y = tipY - perpY * roundness
                val cp2X = tipX + perpX * roundness; val cp2Y = tipY + perpY * roundness
                dst.cubicTo(cp1X, cp1Y, tipX, tipY, tipX, tipY)
                dst.cubicTo(tipX, tipY, cp2X, cp2Y, nextValX, nextValY)
            }
            dst.close()
        }
    }
}
