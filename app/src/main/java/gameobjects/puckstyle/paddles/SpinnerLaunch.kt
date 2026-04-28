package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.SpinnerSkin
import utility.Effects
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation

class SpinnerLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val arm = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val path = Path()
    private val spinDir = if (theme.isWarm) -1f else 1f
    private var spinAngle = 0f

    override var minDist: Float = 0f
        get() = 0f

    override val alwaysVisible: Boolean = true
    override val zIndex: Int
        get() = 2

    private fun drawSpinner(canvas: Canvas, cx: Float, cy: Float) {
        arm.color = responsiveSecondary
        fillPaint.color = theme.shield.primary
        val r = renderer.radius
        val midSize = r * .5f
        val speed = (renderer.movementPower * 0.5f).coerceIn(2f, 10f)
        spinAngle += speed * spinDir
        canvas.withTranslation(cx, cy) {
            rotate(spinAngle)
            val armCount = 4

            for (i in 0 until armCount) {
                withRotation(360f / armCount * i) {
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.quadTo(midSize, r * .5f, r * 0.9f, 0f)
                    path.quadTo(midSize, -r * .5f, 0f, 0f)
                    path.close()
                    drawPath(path, arm)
                }
            }
            for (i in 0 until armCount) {
                withRotation(360f / armCount * i) {
                    arm.color = responsivePrimary
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.quadTo(midSize * .7f, r * .3f, r * 0.7f, 0f)
                    path.quadTo(midSize * .7f, -r * .3f, 0f, 0f)
                    path.close()
                    drawPath(path, arm)
                }
            }


            if (chargeFillRatio > 0f) {
                val fr = r * chargeFillRatio
                for (i in 0 until armCount) {
                    withRotation(360f / armCount * i) {
                        path.reset()
                        path.moveTo(0f, 0f)
                        path.quadTo(fr * 0.5f, fr * .5f, fr * 0.9f, 0f)
                        path.quadTo(fr * 0.5f, -fr * .5f, 0f, 0f)
                        path.close()
                        drawPath(path, fillPaint)
                    }
                }
            }
        }
    }

    override fun drawIdlePaddle(canvas: Canvas) {
        drawSpinner(canvas, renderer.x, renderer.y)
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawSpinner(canvas, paddleX, paddleY)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        drawSpinner(canvas, cx, cy)
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

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 200f).coerceIn(0f, 1f)
            val alpha = (180 * (1f - t * 0.9f)).toInt().coerceIn(100, 255)
            paint.color = color
            paint.alpha = alpha
            paint.strokeWidth = Settings.strokeWidth * 0.6f
            val r = radius * 1.4f
            oval.set(cx - r, cy - r, cx + r, cy + r)
            for (i in 0 until 4) {
                canvas.drawArc(oval, i * 90f + 20f + frame * 2f * spinDir, 50f, false, paint)
            }
            paint.alpha = 255
        }
    }
}
