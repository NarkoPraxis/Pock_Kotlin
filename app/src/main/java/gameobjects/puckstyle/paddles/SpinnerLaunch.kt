package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.SpinnerSkin
import utility.Effects


class SpinnerLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val arm = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
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
        spinAngle += speed * spinDir

        val secColor  = responsiveSecondary
        val primColor = responsivePrimary
        val shieldColor = theme.shield.primary

        scope.drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(spinAngle)
            val armCount = 4

            for (i in 0 until armCount) {
                canvas.save()
                canvas.rotate(armAngleStep * i)

                arm.color = secColor
                path.reset()
                path.moveTo(0f, 0f)
                path.quadTo(midSize, outerHalf, outerTipX, 0f)
                path.quadTo(midSize, -outerHalf, 0f, 0f)
                path.close()
                canvas.drawPath(path, arm)

                arm.color = primColor
                path.reset()
                path.moveTo(0f, 0f)
                path.quadTo(innerCtrl, innerHalf, innerTipX, 0f)
                path.quadTo(innerCtrl, -innerHalf, 0f, 0f)
                path.close()
                canvas.drawPath(path, arm)

                canvas.restore()
            }

            if (chargeFillRatio > 0f) {
                fillPaint.color = shieldColor
                val fr = cachedRadius * chargeFillRatio
                val frHalf = fr * .5f
                val frTipX = fr * 0.9f
                for (i in 0 until armCount) {
                    canvas.save()
                    canvas.rotate(armAngleStep * i)
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.quadTo(frHalf, frHalf, frTipX, 0f)
                    path.quadTo(frHalf, -frHalf, 0f, 0f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                    canvas.restore()
                }
            }

            canvas.restore()
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
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val oval = RectF()
        private var frame = 0
        override val isDone = false

        private val scaledRadius  = radius * 1.4f
        private val cachedStrokeW = Settings.strokeWidth * 0.6f

        init {
            oval.set(cx - scaledRadius, cy - scaledRadius, cx + scaledRadius, cy + scaledRadius)
            paint.strokeWidth = cachedStrokeW
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 200f).coerceIn(0f, 1f)
            val alpha = (180 * (1f - t * 0.9f)).toInt().coerceIn(100, 255)
            paint.color = color
            paint.alpha = alpha
            val baseAngle = frame * 2f * spinDir
            for (i in 0 until 4) {
                canvas.drawArc(oval, i * 90f + 20f + baseAngle, 50f, false, paint)
            }
            paint.alpha = 255
        }
    }
}
