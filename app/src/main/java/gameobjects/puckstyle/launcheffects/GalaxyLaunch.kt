package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect

/** Mini-planet with a ring, perpendicular to aim. */
class GalaxyLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {
    private val body = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val ring = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override fun drawChargingPaddle(canvas: Canvas, puck: Puck) =
        drawPlanet(canvas, puck, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawPlanet(canvas, puck, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawPlanet(canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase, fill: Float) {
        val r = puck.radius * 0.55f
        val pX = -aY
        val pY = aX

        val core = if (ph == ChargePhase.Overcharged) theme.secondary else Color.rgb(80, 40, 140)
        body.color = core
        canvas.drawCircle(cx, cy, r, body)

        if (fill > 0f) {
            body.color = theme.accent
            body.alpha = (220 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * fill, body)
            body.alpha = 255
        }

        ring.color = if (ph == ChargePhase.Overcharged) theme.secondary else Color.rgb(255, 220, 140)
        ring.strokeWidth = Settings.strokeWidth * 0.6f
        val len = paddleHalfLength(puck)
        canvas.drawLine(cx - pX * len, cy - pY * len, cx + pX * len, cy + pY * len, ring)
        // small outer ellipse hint
        canvas.drawCircle(cx, cy, r * 1.35f, ring.apply { alpha = 90 })
        ring.alpha = 255
    }

    override fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        ring.color = Color.rgb(255, 220, 140)
        ring.alpha = (200 * remaining).toInt().coerceIn(0, 255)
        ring.strokeWidth = Settings.strokeWidth * 0.5f
        canvas.drawCircle(rx, ry, puck.radius * (1f + (1f - remaining) * 0.8f), ring)
    }
}
