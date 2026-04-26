package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.Palette
import utility.Effects
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small cartoon cloud. Sweet spot makes it crackle; sweet-spot release fires a lightning bolt
 * into the puck and leaves a rainbow smear.
 */
class RainbowLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

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
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Inert else ChargePhase.Building
        drawCloud(canvas, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
        if (sweet) drawBolt(canvas, cx, cy, progress)
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val r = renderer.radius * 0.4f
        val color = if (ph == ChargePhase.Inert) theme.main.secondary else Color.rgb(230, 235, 245)
        cloud.color = color
        canvas.drawCircle(cx, cy, r * 1.1f, cloud)
        canvas.drawCircle(cx - r * 0.9f, cy + r * 0.2f, r * 0.8f, cloud)
        canvas.drawCircle(cx + r * 0.9f, cy + r * 0.2f, r * 0.8f, cloud)
        canvas.drawCircle(cx, cy - r * 0.55f, r * 0.85f, cloud)

        if (fill > 0f) {
            cloud.color = theme.effect.primary
            cloud.alpha = (200 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * 0.8f * fill, cloud)
            cloud.alpha = 255
        }
    }

    private fun drawCrackle(canvas: Canvas, cx: Float, cy: Float) {
        val len = renderer.radius * (0.3f + 0.15f * sin(frame * 1.3f))
        bolt.color = Palette.cyclingHue(frame, 6f)
        bolt.strokeWidth = Settings.strokeWidth * 0.4f
        canvas.drawLine(cx - len, cy, cx + len * 0.4f, cy + len * 0.4f, bolt)
        canvas.drawLine(cx + len * 0.4f, cy + len * 0.4f, cx + len, cy - len * 0.2f, bolt)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, progress: Float) {
        val px = renderer.x
        val py = renderer.y
        bolt.color = Palette.cyclingHue(frame, 12f)
        bolt.strokeWidth = Settings.strokeWidth * (1.2f - progress)
        val midX = (cx + px) / 2f + (renderer.radius * 0.4f) * if ((frame / 2) % 2 == 0) 1f else -1f
        val midY = (cy + py) / 2f
        canvas.drawLine(cx, cy, midX, midY, bolt)
        canvas.drawLine(midX, midY, px, py, bolt)
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(RainbowSmear(rx, ry, aX, aY, renderer.radius))
    }

    private class RainbowSmear(
        private val cx: Float, private val cy: Float,
        private val aX: Float, private val aY: Float,
        private val radius: Float
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 240f).coerceIn(0f, 1f)
            val sat = 1f - t * 0.85f
            val alpha = (155 * (1f - t * 0.65f)).toInt().coerceIn(0, 255)
            paint.strokeWidth = radius * 0.18f
            val baseAngle = atan2(aY, aX) + Math.PI.toFloat() / 2f
            val len = radius * 1.1f
            for (i in 0 until 5) {
                val angle = baseAngle + (i - 2f) * 0.28f
                paint.color = Palette.hsv(i * 72f, sat, 0.9f)
                paint.alpha = alpha
                canvas.drawLine(
                    cx - cos(angle) * len, cy - sin(angle) * len,
                    cx + cos(angle) * len, cy + sin(angle) * len,
                    paint
                )
            }
            paint.alpha = 255
        }
    }
}
