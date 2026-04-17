package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import kotlin.math.sin

/** Translucent ghost-puck double trailing behind the real one. */
class GhostLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val body = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val outline = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = Settings.strokeWidth * 0.8f
    }

    override fun drawChargingPaddle(canvas: Canvas) =
        drawGhost(canvas, paddleX, paddleY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawGhost(canvas, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawGhost(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val r = currentRenderer.radius * (0.75f + 0.05f * sin(frame * 0.2f))
        val base = if (ph == ChargePhase.Overcharged) theme.secondary else theme.primary
        body.color = base
        body.alpha = 90
        canvas.drawCircle(cx, cy, r, body)
        outline.color = base
        outline.alpha = 160
        canvas.drawCircle(cx, cy, r, outline)

        if (fill > 0f) {
            body.color = theme.accent
            body.alpha = (150 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * fill, body)
        }
        body.alpha = 255
        outline.alpha = 255
    }

    override fun drawResidual(canvas: Canvas, rx: Float, ry: Float, remaining: Float) {
        body.color = theme.accent
        body.alpha = (140 * remaining).toInt().coerceIn(0, 255)
        canvas.drawCircle(rx, ry, currentRenderer.radius * (1.2f + (1f - remaining)), body)
        body.alpha = 255
    }
}
