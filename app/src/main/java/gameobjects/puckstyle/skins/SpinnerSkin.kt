package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import utility.Effects

class SpinnerSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    private val baseFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rim = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        baseFill.color = colors.primary
        rim.color = colors.secondary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, baseFill)
        rim.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rim)
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
            val alpha = (255 - 155 * t).toInt().coerceIn(100, 255)
            arm.color = armColor
            arm.alpha = alpha
            val r = radius
            canvas.withTranslation(cx, cy) {
                rotate(frame * 2f * spinDir)
                val armCount = 4
                for (i in 0 until armCount) {
                    withRotation(360f / armCount * i) {
                        path.reset()
                        path.moveTo(0f, 0f)
                        path.quadTo(r * 0.45f, r * 0.15f, r * 0.9f, 0f)
                        path.quadTo(r * 0.45f, -r * 0.15f, 0f, 0f)
                        path.close()
                        drawPath(path, arm)
                    }
                }
            }
        }
    }
}
