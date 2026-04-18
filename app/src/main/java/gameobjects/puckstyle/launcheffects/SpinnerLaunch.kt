package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect

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
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawCross(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f, frame * 0.35f + progress * 3f)
    }

    private fun drawCross(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float, spin: Float
    ) {
        canvas.save()
        canvas.rotate(Math.toDegrees(spin.toDouble()).toFloat(), cx, cy)
        val half = paddleHalfLength() * 0.9f
        val pX = -aY
        val pY = aX
        bar.color = theme.secondary
        bar.strokeWidth = paddleThickness()
        canvas.drawLine(cx - pX * half, cy - pY * half, cx + pX * half, cy + pY * half, bar)
        canvas.drawLine(cx - aX * half, cy - aY * half, cx + aX * half, cy + aY * half, bar)
        if (fill > 0f) {
            bar.color = theme.accent
            val fh = half * fill
            canvas.drawLine(cx - pX * fh, cy - pY * fh, cx + pX * fh, cy + pY * fh, bar)
            canvas.drawLine(cx - aX * fh, cy - aY * fh, cx + aX * fh, cy + aY * fh, bar)
        }
        canvas.restore()
    }

    override fun drawResidual(canvas: Canvas, rx: Float, ry: Float, remaining: Float) {
        bar.color = theme.accent
        bar.alpha = (200 * remaining).toInt().coerceIn(0, 255)
        bar.strokeWidth = Settings.strokeWidth * 0.6f
        bar.style = Paint.Style.STROKE
        canvas.drawCircle(rx, ry, currentRenderer.radius * (1f + (1f - remaining) * 1.2f), bar)
        bar.alpha = 255
    }
}
