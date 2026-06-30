package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.puckstyle.PuckRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import gameobjects.puckstyle.PuckSkin
import physics.Point
import utility.Effects

class SpinnerSkin(override val renderer: PuckRenderer) : PuckSkin {

    private val spinDir = if (theme.isWarm) -1f else 1f
    private var spinAngle = 0f
    /** Live spin of the ball's arms, read by [gameobjects.puckstyle.paddles.SpinnerLaunch] to
     *  re-align the paddle's own spin after a score toss (keeps blades and arms in phase). */
    val currentSpinAngle: Float get() = spinAngle
    private val path = Path()

    private var celebrationActive = false
    private var celebrationFrame = 0
    private val CELEB_EXPAND = 20
    private val CELEB_HOLD = 10
    private val CELEB_RETRACT = 30
    private val CELEB_TOTAL = CELEB_EXPAND + CELEB_HOLD + CELEB_RETRACT

    private val armAngleStep = 360f / 8

    private val celebrationT: Float
        get() {
            if (!celebrationActive) return 0f
            return when {
                celebrationFrame <= CELEB_EXPAND -> celebrationFrame.toFloat() / CELEB_EXPAND
                celebrationFrame <= CELEB_EXPAND + CELEB_HOLD -> 1f
                else -> {
                    val r = celebrationFrame - CELEB_EXPAND - CELEB_HOLD
                    1f - r.toFloat() / CELEB_RETRACT
                }
            }.coerceIn(0f, 1f)
        }

    override fun DrawScope.drawBody() {
        val colors = responsiveGroup
        val center = Offset(renderer.x, renderer.y)

        drawCircle(Color(colors.primary), renderer.radius, center)
        drawCircle(
            Color(colors.secondary),
            renderer.radius,
            center,
            style = Stroke(width = renderer.strokeWidth * 0.7f)
        )

        val r = renderer.radius
        val speed = (renderer.movementPower * 0.5f).coerceIn(2f, 10f)
        // Static UI: drive the spin off the strobe clock so the arms keep spinning in place (frame is
        // frozen in the preview); live play accumulates per frame as before.
        spinAngle = if (renderer.staticUiMode) renderer.strobe * 4f * spinDir
                    else spinAngle + speed * spinDir
        val armCount = 8
        val baseTipDist = (renderer.movementPower / 30f).coerceIn(.5f, r) * r * 2f
        val ct = celebrationT
        val tipDist = if (ct > 0f) baseTipDist + (r * 3f - baseTipDist) * ct else baseTipDist
        if (celebrationActive) {
            celebrationFrame++
            if (celebrationFrame >= CELEB_TOTAL) { celebrationActive = false; celebrationFrame = 0 }
        }

        val armColor = Color(responsiveSecondary)
        val tipHalf  = tipDist * 0.5f
        val tipCtrlX = tipDist * 0.55f
        val toRad = PI.toFloat() / 180f

        val ox = renderer.x
        val oy = renderer.y
        for (i in 0 until armCount) {
            val rad = (spinAngle + armAngleStep * i) * toRad
            val cosA = cos(rad); val sinA = sin(rad)
            // Tip point (tipDist, 0) and the two control points (tipCtrlX, ±tipHalf), rotated.
            val tipX  = ox + tipDist * cosA
            val tipY  = oy + tipDist * sinA
            val c1x   = ox + tipCtrlX * cosA - tipHalf * sinA
            val c1y   = oy + tipCtrlX * sinA + tipHalf * cosA
            val c2x   = ox + tipCtrlX * cosA + tipHalf * sinA
            val c2y   = oy + tipCtrlX * sinA - tipHalf * cosA
            path.reset()
            path.moveTo(ox, oy)
            path.quadraticTo(c1x, c1y, tipX, tipY)
            path.quadraticTo(c2x, c2y, ox, oy)
            path.close()
            drawPath(path, armColor)
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        Effects.addPersistentEffect(SpinnerResidual(renderer.x, renderer.y, renderer.radius, renderer.bakedPrimary(theme.main.primary), if (theme.isWarm) -1f else 1f))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(SpinnerResidual(renderer.x, renderer.y, renderer.radius, renderer.bakedPrimary(theme.main.primary), if (theme.isWarm) -1f else 1f))
    }

    override val explosionFrequency get() = 45
    override val scatterDensity get() = 1.3f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        celebrationActive = true
        celebrationFrame = 0
        // Standalone leaf-burst so a Spinner score reads like the other balls' bursts — and so it
        // still shows when replayed off a renderer-detached skin (the paddle-toss arrival path).
        Effects.addPersistentEffect(SpinnerCelebration(position.x, position.y, renderer.radius, renderer.bakedPrimary(theme.main.primary), spinDir))
    }

    override fun onVictory(x: Float, y: Float) {
        celebrationActive = true
        celebrationFrame = 0
        Effects.addPersistentEffect(SpinnerResidual(x, y, renderer.radius, renderer.bakedPrimary(theme.main.primary), if (theme.isWarm) -1f else 1f, asVictory = true))
    }

    class SpinnerResidual(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val armColor: Int,
        private val spinDir: Float,
        private val asVictory: Boolean = false,
    ) : Effects.PersistentEffect {
        private val path = Path()
        private var frame = 0
        private var victoryDone = false
        override val isDone get() = victoryDone

        private val V_GROW   = 40
        private val V_HOLD   = 20
        private val V_SHRINK = 50
        private val V_TOTAL  = V_GROW + V_HOLD + V_SHRINK

        private val armAngleStep = 360f / 4

        private val midSize   = radius * .5f
        private val outerTipX = radius * 0.9f
        private val outerHalf = radius * .5f
        private val innerCtrl = midSize * .7f
        private val innerTipX = radius * 0.7f
        private val innerHalf = radius * .3f

        override fun step() {
            frame++
            if (asVictory && frame >= V_TOTAL) victoryDone = true
        }

        override fun draw(scope: DrawScope) {
            val alpha: Int
            val scale: Float
            if (asVictory) {
                val t = when {
                    frame <= V_GROW -> frame.toFloat() / V_GROW
                    frame <= V_GROW + V_HOLD -> 1f
                    else -> 1f - (frame - V_GROW - V_HOLD).toFloat() / V_SHRINK
                }.coerceIn(0f, 1f)
                val eased = t * t * (3f - 2f * t)
                scale = eased * 3f
                alpha = (255 * eased).toInt().coerceIn(0, 255)
            } else {
                scale = 1f
                val t = (frame / 200f).coerceIn(0f, 1f)
                alpha = (255 - 155 * t).toInt().coerceIn(50, 255)
            }

            val drawColor = Color(armColor).copy(alpha = alpha / 255f)

            // Draw in absolute screen coordinates to avoid per-frame capturing withTransform lambdas.
            // Composite transform = translate(cx,cy) -> [scale] -> rotate(baseDeg) -> rotate(armDeg).
            val baseDeg = frame * 2f * spinDir
            val s = if (asVictory) scale else 1f
            val armCount = 4
            for (i in 0 until armCount) {
                val deg = baseDeg + armAngleStep * i
                val rad = deg * (PI.toFloat() / 180f)
                val cosA = cos(rad); val sinA = sin(rad)
                // Local point (lx,ly) -> scaled -> rotated -> translated.
                // outer blade
                drawResidualBlade(scope, drawColor, cx, cy, s, cosA, sinA, midSize, outerHalf, outerTipX)
                // inner blade
                drawResidualBlade(scope, drawColor, cx, cy, s, cosA, sinA, innerCtrl, innerHalf, innerTipX)
            }
        }

        private fun drawResidualBlade(
            scope: DrawScope, drawColor: Color,
            cx: Float, cy: Float, s: Float,
            cosA: Float, sinA: Float,
            ctrl: Float, half: Float, tipX: Float
        ) {
            val cX = ctrl * s; val hY = half * s; val tX = tipX * s
            // control 1 (ctrl, half), tip (tipX, 0), control 2 (ctrl, -half), origin (0,0)
            val p0x = cx; val p0y = cy
            val c1x = cx + cX * cosA - hY * sinA
            val c1y = cy + cX * sinA + hY * cosA
            val tpx = cx + tX * cosA
            val tpy = cy + tX * sinA
            val c2x = cx + cX * cosA + hY * sinA
            val c2y = cy + cX * sinA - hY * cosA
            path.reset()
            path.moveTo(p0x, p0y)
            path.quadraticTo(c1x, c1y, tpx, tpy)
            path.quadraticTo(c2x, c2y, p0x, p0y)
            path.close()
            scope.drawPath(path, drawColor)
        }
    }

    /**
     * Score celebration built from the [SpinnerResidual] leaves — the four spinning lines (the arc
     * "mark") are intentionally omitted, just the leaves. It begins identical to the residual (four
     * leaves overlaid at the centre) then the four leaves fly outward as they keep spinning, reaching
     * ~3× ball radius from where they started before fading away. The set of four is emitted three
     * times in quick succession ([WAVES] staggered by [STAGGER] frames); because each wave is farther
     * along its spin when it launches, the trailing waves lag the leading one and the whole thing
     * reads as a short expanding spiral. Unlike the residual it does not linger: it self-finishes.
     * Used by the scoring celebrations only ([onUsedToScore] and the paddle-toss arrival,
     * [gameobjects.puckstyle.BallStyleFactory.spawnPaddleScoreCelebration] → Spinner).
     */
    class SpinnerCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val armColor: Int,
        private val spinDir: Float,
    ) : Effects.PersistentEffect {
        private val path = Path()
        private var frame = 0
        override val isDone get() = frame >= TOTAL

        private val maxSpread = radius * 3f
        private val midSize   = radius * .5f
        private val outerTipX = radius * 0.9f
        private val outerHalf = radius * .5f
        private val innerCtrl = midSize * .7f
        private val innerTipX = radius * 0.7f
        private val innerHalf = radius * .3f

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val armCount = 4
            // Three staggered waves of four leaves. Each wave runs the same burst on its own clock
            // (offset by STAGGER); the later starts spin/spread behind the earlier, tracing a spiral.
            for (w in 0 until WAVES) {
                val local = frame - w * STAGGER
                if (local < 0) continue   // this wave hasn't launched yet
                val t = (local.toFloat() / WAVE_FRAMES).coerceIn(0f, 1f)
                // Leaves burst out fast then ease to a stop at ~3× radius (smoothstep), fading as they go.
                val spreadT = t * t * (3f - 2f * t)
                val dist = maxSpread * spreadT
                val drawColor = Color(armColor).copy(alpha = (1f - t * t).coerceIn(0f, 1f))
                // Keep spinning while spreading (same cadence as the residual), on this wave's clock,
                // plus a fixed per-wave offset (0°, 30°, 60°…) so the waves fan out around the centre
                // instead of chasing down the same arm.
                val baseDeg = local * 2f * spinDir + w * WAVE_SPREAD_DEG * spinDir
                for (i in 0 until armCount) {
                    val rad = (baseDeg + 90f * i) * (PI.toFloat() / 180f)
                    val cosA = cos(rad); val sinA = sin(rad)
                    // Each leaf's origin flies outward along the direction it currently points.
                    val ox = cx + cosA * dist
                    val oy = cy + sinA * dist
                    drawLeaf(scope, drawColor, ox, oy, cosA, sinA, midSize, outerHalf, outerTipX)
                    drawLeaf(scope, drawColor, ox, oy, cosA, sinA, innerCtrl, innerHalf, innerTipX)
                }
            }
        }

        private fun drawLeaf(
            scope: DrawScope, drawColor: Color,
            ox: Float, oy: Float,
            cosA: Float, sinA: Float,
            ctrl: Float, half: Float, tipX: Float
        ) {
            // control 1 (ctrl, half), tip (tipX, 0), control 2 (ctrl, -half), origin (0,0)
            val c1x = ox + ctrl * cosA - half * sinA
            val c1y = oy + ctrl * sinA + half * cosA
            val tpx = ox + tipX * cosA
            val tpy = oy + tipX * sinA
            val c2x = ox + ctrl * cosA + half * sinA
            val c2y = oy + ctrl * sinA - half * cosA
            path.reset()
            path.moveTo(ox, oy)
            path.quadraticTo(c1x, c1y, tpx, tpy)
            path.quadraticTo(c2x, c2y, ox, oy)
            path.close()
            scope.drawPath(path, drawColor)
        }

        companion object {
            private const val WAVES = 3          // how many four-leaf bursts
            private const val STAGGER = 8        // frames between successive waves (the spiral lag)
            private const val WAVE_SPREAD_DEG = 30f  // angular offset per wave so they fan out, not trail
            private const val WAVE_FRAMES = 48   // each wave's own spread+fade lifetime
            // Last wave launches at (WAVES-1)*STAGGER, then needs WAVE_FRAMES to finish.
            private const val TOTAL = (WAVES - 1) * STAGGER + WAVE_FRAMES
        }
    }
}
