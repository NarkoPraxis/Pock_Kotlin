package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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

        for (i in 0 until armCount) {
            val rad = (spinAngle + armAngleStep * i) * toRad
            val cosA = cos(rad); val sinA = sin(rad)
            fun sx(lx: Float, ly: Float) = renderer.x + lx * cosA - ly * sinA
            fun sy(lx: Float, ly: Float) = renderer.y + lx * sinA + ly * cosA
            path.reset()
            path.moveTo(renderer.x, renderer.y)
            path.quadraticTo(sx(tipCtrlX, tipHalf), sy(tipCtrlX, tipHalf), sx(tipDist, 0f), sy(tipDist, 0f))
            path.quadraticTo(sx(tipCtrlX, -tipHalf), sy(tipCtrlX, -tipHalf), renderer.x, renderer.y)
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

            scope.withTransform({
                translate(cx, cy)
                if (asVictory) scale(scale, scale)
                rotate(frame * 2f * spinDir)
            }) {
                val armCount = 4
                for (i in 0 until armCount) {
                    withTransform({ rotate(armAngleStep * i) }) {
                        path.reset()
                        path.moveTo(0f, 0f)
                        path.quadraticTo(midSize, outerHalf, outerTipX, 0f)
                        path.quadraticTo(midSize, -outerHalf, 0f, 0f)
                        path.close()
                        drawPath(path, drawColor)

                        path.reset()
                        path.moveTo(0f, 0f)
                        path.quadraticTo(innerCtrl, innerHalf, innerTipX, 0f)
                        path.quadraticTo(innerCtrl, -innerHalf, 0f, 0f)
                        path.close()
                        drawPath(path, drawColor)
                    }
                }
            }
        }
    }
}
