package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import kotlin.math.sin

/**
 * A small cartoon cloud. Sweet spot makes it crackle; sweet-spot release fires a lightning bolt
 * into the puck and leaves a rainbow glow.
 */
class RainbowLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val cloud = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val bolt = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawCloud(canvas, paddleX, paddleY, phase, chargeFillRatio)
        if (phase == ChargePhase.SweetSpot) drawCrackle(canvas, paddleX, paddleY)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawCloud(canvas, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
        if (sweet) drawBolt(canvas, cx, cy, progress)
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val r = currentRenderer.radius * 0.4f
        val color = if (ph == ChargePhase.Overcharged) theme.secondary else Color.rgb(230, 235, 245)
        cloud.color = color
        canvas.drawCircle(cx, cy, r * 1.1f, cloud)
        canvas.drawCircle(cx - r * 0.9f, cy + r * 0.2f, r * 0.8f, cloud)
        canvas.drawCircle(cx + r * 0.9f, cy + r * 0.2f, r * 0.8f, cloud)
        canvas.drawCircle(cx, cy - r * 0.55f, r * 0.85f, cloud)

        if (fill > 0f) {
            cloud.color = theme.accent
            cloud.alpha = (200 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * 0.8f * fill, cloud)
            cloud.alpha = 255
        }
    }

    private fun drawCrackle(canvas: Canvas, cx: Float, cy: Float) {
        val len = currentRenderer.radius * (0.3f + 0.15f * sin(frame * 1.3f))
        bolt.color = Palette.cyclingHue(frame, 6f)
        bolt.strokeWidth = Settings.strokeWidth * 0.4f
        canvas.drawLine(cx - len, cy, cx + len * 0.4f, cy + len * 0.4f, bolt)
        canvas.drawLine(cx + len * 0.4f, cy + len * 0.4f, cx + len, cy - len * 0.2f, bolt)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, progress: Float) {
        val px = currentRenderer.x
        val py = currentRenderer.y
        bolt.color = Palette.cyclingHue(frame, 12f)
        bolt.strokeWidth = Settings.strokeWidth * (1.2f - progress)
        val midX = (cx + px) / 2f + (currentRenderer.radius * 0.4f) * if ((frame / 2) % 2 == 0) 1f else -1f
        val midY = (cy + py) / 2f
        canvas.drawLine(cx, cy, midX, midY, bolt)
        canvas.drawLine(midX, midY, px, py, bolt)
    }

    override fun drawResidual(canvas: Canvas, rx: Float, ry: Float, remaining: Float) {
        bolt.style = Paint.Style.STROKE
        bolt.strokeWidth = Settings.strokeWidth * 0.8f
        for (i in 0 until 5) {
            bolt.color = Palette.hsv(i * 60f + frame * 2f, 1f, 1f)
            bolt.alpha = (180 * remaining).toInt().coerceIn(0, 255)
            canvas.drawCircle(rx, ry, currentRenderer.radius * (1f + i * 0.15f) * (1f + (1f - remaining) * 0.5f), bolt)
        }
        bolt.alpha = 255
    }
}
