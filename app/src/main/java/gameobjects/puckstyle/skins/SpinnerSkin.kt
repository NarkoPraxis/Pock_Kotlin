package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import utility.Effects

class SpinnerSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    private val baseFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rim = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    private val spinDir = if (theme.isWarm) -1f else 1f
    private var spinAngle = 0f
    private val path = Path()


    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        baseFill.color = colors.primary
        rim.color = colors.secondary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, baseFill)
        rim.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rim)

        val r = renderer.radius
        val speed = (renderer.movementPower * 0.5f).coerceIn(2f, 10f)
        spinAngle += speed * spinDir
        val armCount = 8
        val tipDist = (renderer.movementPower / 30f).coerceIn(.5f, r) * r * 2f

        for (i in 0 until armCount) {
            canvas.withTranslation(renderer.x, renderer.y) {
                rotate(spinAngle)
                withRotation(360f / armCount * i) {
                    baseFill.color = responsiveSecondary
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.quadTo(tipDist * 0.55f, tipDist * 0.5f, tipDist, 0f)
                    path.quadTo(tipDist * 0.55f, -tipDist * 0.5f, 0f, 0f)
                    path.close()
                    drawPath(path, baseFill)
                }
            }
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        Effects.addPersistentEffect(SpinnerResidual(renderer.x, renderer.y, renderer.radius, theme.main.primary, if (theme.isWarm) -1f else 1f))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(SpinnerResidual(renderer.x, renderer.y, renderer.radius, theme.main.primary, if (theme.isWarm) -1f else 1f))
    }
    class SpinnerResidual(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val armColor: Int,
        private val spinDir: Float,
    ) : Effects.PersistentEffect {
        private val arm = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val path = Path()
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 200f).coerceIn(0f, 1f)
            val alpha = (255 - 155 * t).toInt().coerceIn(50, 255)
            arm.color = armColor
            arm.alpha = alpha
            val r = radius
            val midSize = r * .5f

            canvas.withTranslation(cx, cy) {
                rotate(frame * 2f * spinDir)
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
                        arm.color = armColor
                        path.reset()
                        path.moveTo(0f, 0f)
                        path.quadTo(midSize * .7f, r * .3f, r * 0.7f, 0f)
                        path.quadTo(midSize * .7f, -r * .3f, 0f, 0f)
                        path.close()
                        drawPath(path, arm)
                    }
                }
            }
        }
    }
}
