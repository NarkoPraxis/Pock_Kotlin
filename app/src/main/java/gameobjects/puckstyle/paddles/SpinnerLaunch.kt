package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import utility.Effects
import androidx.core.graphics.withRotation

/** Spinning shuriken cross — two bars crossed, rotating while charging. */
class SpinnerLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val bar = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawCross(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio, frame * 0.35f)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Inert else ChargePhase.Building
        drawCross(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f, frame * 0.35f + progress * 3f)
    }

    private fun drawCross(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float, spin: Float
    ) {
        canvas.withRotation(Math.toDegrees(spin.toDouble()).toFloat(), cx, cy) {
            val half = paddleHalfLength() * 0.9f
            val pX = -aY
            val pY = aX
            bar.color = theme.main.secondary
            bar.strokeWidth = paddleThickness()
            drawLine(cx - pX * half, cy - pY * half, cx + pX * half, cy + pY * half, bar)
            drawLine(cx - aX * half, cy - aY * half, cx + aX * half, cy + aY * half, bar)
            if (fill > 0f) {
                bar.color = theme.effect.primary
                val fh = half * fill
                drawLine(cx - pX * fh, cy - pY * fh, cx + pX * fh, cy + pY * fh, bar)
                drawLine(cx - aX * fh, cy - aY * fh, cx + aX * fh, cy + aY * fh, bar)
            }
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(SpinnerMark(rx, ry, currentRenderer.radius, theme.effect.primary))
    }

    private class SpinnerMark(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val oval = RectF()
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 200f).coerceIn(0f, 1f)
            val alpha = (180 * (1f - t * 0.9f)).toInt().coerceIn(0, 255)
            if (alpha <= 0) return
            paint.color = color
            paint.alpha = alpha
            paint.strokeWidth = Settings.strokeWidth * 0.6f
            val r = radius * 1.4f
            oval.set(cx - r, cy - r, cx + r, cy + r)
            // 4 curved arc segments arranged radially, like residual smear of spinning blades
            for (i in 0 until 4) {
                canvas.drawArc(oval, i * 90f + 20f, 50f, false, paint)
            }
            paint.alpha = 255
        }
    }
}
