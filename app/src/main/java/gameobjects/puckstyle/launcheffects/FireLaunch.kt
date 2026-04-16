package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import kotlin.math.sin

/** Tethered fireball: a mini flame instead of a paddle bar. Sweet-spot leaves a scorch mark. */
class FireLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val flamePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val scorchPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun drawChargingPaddle(canvas: Canvas, puck: Puck) {
        drawFireball(canvas, puck, paddleX, paddleY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawFireball(canvas, puck, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawFireball(canvas: Canvas, puck: Puck, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val base = puck.radius * 0.6f
        val jitter = 1f + 0.08f * sin(frame * 0.9f)
        val outerR = base * jitter

        val outerColor = when (ph) {
            ChargePhase.Overcharged -> theme.secondary
            else -> Color.rgb(255, 140, 30)
        }
        flamePaint.color = outerColor
        flamePaint.alpha = 255
        canvas.drawCircle(cx, cy, outerR, flamePaint)

        if (fill > 0f) {
            val coreColor = if (ph == ChargePhase.SweetSpot) theme.accent else Color.rgb(255, 230, 120)
            flamePaint.color = coreColor
            val pulse = if (ph == ChargePhase.SweetSpot) 0.8f + 0.2f * sin(frame * 0.4f) else 1f
            flamePaint.alpha = (255 * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, outerR * 0.6f * fill, flamePaint)
        }
    }

    override fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        scorchPaint.color = Color.rgb(40, 20, 10)
        scorchPaint.alpha = (180 * remaining).toInt().coerceIn(0, 255)
        canvas.drawCircle(rx, ry, puck.radius * (0.9f + (1f - remaining) * 0.3f), scorchPaint)
    }

    override fun paddleHalfLength(puck: Puck): Float = puck.radius * 0.6f
    override fun paddleThickness(puck: Puck): Float = Settings.strokeWidth
}
