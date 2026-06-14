package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.skins.SpinnerSkin
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SpinnerLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val path = Path()
    private val spinDir = if (theme.isWarm) -1f else 1f
    private var spinAngle = 0f

    private val armAngleStep = 90f

    private var cachedRadius = -1f
    private var midSize  = 0f
    private var outerTipX = 0f
    private var outerHalf = 0f
    private var innerCtrl = 0f
    private var innerTipX = 0f
    private var innerHalf = 0f

    private fun ensureRadiusCache() {
        val r = renderer.radius
        if (cachedRadius != r) {
            cachedRadius = r
            midSize   = r * .5f
            outerTipX = r * 0.9f
            outerHalf = r * .5f
            innerCtrl = midSize * .7f
            innerTipX = r * 0.7f
            innerHalf = r * .3f
        }
    }

    override var minDist: Float = 0f
        get() = 0f

    override val alwaysVisible: Boolean = true
    override val zIndex: Int
        get() = 2

    private fun drawSpinner(scope: DrawScope, cx: Float, cy: Float) {
        ensureRadiusCache()
        val speed = (renderer.movementPower * 0.5f).coerceIn(2f, 10f)
        // Static UI: drive the spin off the strobe clock so the blades keep spinning in place (the
        // paddle frame is frozen in the Ball Designer); live play accumulates per frame as before.
        spinAngle = if (renderer.staticUiMode) animFrame * 4f * spinDir
                    else spinAngle + speed * spinDir

        val secColor   = responsiveSecondary
        val primColor  = responsivePrimary
        val shieldColor = theme.shield.primary

        val armCount = 4
        val armStepRad = armAngleStep * PI.toFloat() / 180f

        for (i in 0 until armCount) {
            val θ = (spinAngle + armAngleStep * i) * PI.toFloat() / 180f
            val cosA = cos(θ); val sinA = sin(θ)
            fun sx(lx: Float, ly: Float) = cx + lx * cosA - ly * sinA
            fun sy(lx: Float, ly: Float) = cy + lx * sinA + ly * cosA

            path.reset()
            path.moveTo(sx(0f, 0f), sy(0f, 0f))
            path.quadraticTo(sx(midSize, outerHalf), sy(midSize, outerHalf), sx(outerTipX, 0f), sy(outerTipX, 0f))
            path.quadraticTo(sx(midSize, -outerHalf), sy(midSize, -outerHalf), sx(0f, 0f), sy(0f, 0f))
            path.close()
            scope.drawPath(path, Color(secColor))

            path.reset()
            path.moveTo(sx(0f, 0f), sy(0f, 0f))
            path.quadraticTo(sx(innerCtrl, innerHalf), sy(innerCtrl, innerHalf), sx(innerTipX, 0f), sy(innerTipX, 0f))
            path.quadraticTo(sx(innerCtrl, -innerHalf), sy(innerCtrl, -innerHalf), sx(0f, 0f), sy(0f, 0f))
            path.close()
            scope.drawPath(path, Color(primColor))
        }

        if (chargeFillRatio > 0f) {
            val fr = cachedRadius * chargeFillRatio
            val frHalf = fr * .5f
            val frTipX = fr * 0.9f
            for (i in 0 until armCount) {
                val θ = (spinAngle + armAngleStep * i) * PI.toFloat() / 180f
                val cosA = cos(θ); val sinA = sin(θ)
                fun sx(lx: Float, ly: Float) = cx + lx * cosA - ly * sinA
                fun sy(lx: Float, ly: Float) = cy + lx * sinA + ly * cosA

                path.reset()
                path.moveTo(sx(0f, 0f), sy(0f, 0f))
                path.quadraticTo(sx(frHalf, frHalf), sy(frHalf, frHalf), sx(frTipX, 0f), sy(frTipX, 0f))
                path.quadraticTo(sx(frHalf, -frHalf), sy(frHalf, -frHalf), sx(0f, 0f), sy(0f, 0f))
                path.close()
                scope.drawPath(path, Color(shieldColor))
            }
        }
    }

    override fun drawIdlePaddle(scope: DrawScope) {
        drawSpinner(scope, renderer.x, renderer.y)
    }

    override fun drawChargingPaddle(scope: DrawScope) {
        drawSpinner(scope, paddleX, paddleY)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        drawSpinner(scope, cx, cy)
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(SpinnerMark(rx, ry, renderer.radius, theme.main.primary, spinDir))
        Effects.addPersistentEffect(SpinnerSkin.SpinnerResidual(rx, ry, renderer.radius, theme.shield.primary, spinDir))
    }

    private class SpinnerMark(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int,
        private val spinDir: Float
    ) : Effects.PersistentEffect {
        private val scaledRadius  = radius * 1.4f
        private val cachedStrokeW = Settings.strokeWidth * 0.6f
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val t = (frame / 200f).coerceIn(0f, 1f)
            val alpha = (180 * (1f - t * 0.9f)).toInt().coerceIn(100, 255)
            val baseAngle = frame * 2f * spinDir
            val center = Offset(cx, cy)
            for (i in 0 until 4) {
                scope.drawArc(
                    color = Color(Palette.withAlpha(color, alpha)),
                    startAngle = i * 90f + 20f + baseAngle,
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = Offset(cx - scaledRadius, cy - scaledRadius),
                    size = Size(scaledRadius * 2, scaledRadius * 2),
                    style = Stroke(width = cachedStrokeW, cap = StrokeCap.Round)
                )
            }
        }
    }
}
