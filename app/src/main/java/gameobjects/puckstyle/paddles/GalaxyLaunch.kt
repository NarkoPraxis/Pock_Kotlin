package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import utility.Effects

/** Mini-planet with a ring, perpendicular to aim. */
class GalaxyLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {
    private val body = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val ring = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override fun drawChargingPaddle(canvas: Canvas) =
        drawPlanet(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawPlanet(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
    }

    private fun drawPlanet(canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase, fill: Float) {
        val r = renderer.radius * 0.55f
        val pX = -aY
        val pY = aX

        body.color = responsivePrimary
        canvas.drawCircle(cx, cy, r, body)

        if (fill > 0f) {
            body.color = theme.effect.primary
            body.alpha = (220 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * fill, body)
            body.alpha = 255
        }

        ring.color = if (ph == ChargePhase.Inert) theme.inert.secondary else responsivePrimary
        ring.strokeWidth = Settings.strokeWidth * 0.6f
        val len = paddleHalfLength()
        canvas.drawLine(cx - pX * len, cy - pY * len, cx + pX * len, cy + pY * len, ring)
        canvas.drawCircle(cx, cy, r * 1.35f, ring.apply { alpha = 90 })
        ring.alpha = 255
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(NebulaMark(rx, ry, renderer.radius, theme.effect.primary))
    }

    private class NebulaMark(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame.toFloat() / 120f).coerceIn(0f, 1f)
            val r = radius * (0.8f + t * 1.2f)
            val alpha = (75 * (1f - t * 0.7f)).toInt().coerceIn(0, 255)
            paint.color = color
            paint.alpha = alpha
            canvas.drawCircle(cx, cy, r, paint)
            paint.alpha = (alpha * 0.5f).toInt()
            canvas.drawCircle(cx, cy, r * 0.45f, paint)
            paint.alpha = 255
        }
    }
}
