package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import utility.Effects

/** Classic paddle: a clean perpendicular bar that fills center-out with purple. */
class ClassicLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(ClassicResidual(rx, ry, renderer.radius, theme.main.primary))
    }

    private class ClassicResidual(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame.toFloat() / 30f).coerceIn(0f, 1f)
            val r = radius * (1f + t)
            val alpha = (255f - 255f * (t- 0.2f)).toInt().coerceIn(0, 255)
            paint.color = color
            paint.alpha = alpha
            paint.strokeWidth = Settings.strokeWidth * 2f
            canvas.drawCircle(cx, cy, r, paint)
        }
    }
}
