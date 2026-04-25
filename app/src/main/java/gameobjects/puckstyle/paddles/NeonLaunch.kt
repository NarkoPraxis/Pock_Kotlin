package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import utility.Effects
import kotlin.math.sin

/** Glow-stick: paddle bar with an outer halo glow. Sweet spot flickers at a distinct frequency. */
class NeonLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {
    private val halo = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawHalo(canvas, paddleX, paddleY, aimX, aimY, phase)
        super.drawChargingPaddle(canvas)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Inert else ChargePhase.Building
        drawHalo(canvas, cx, cy, aX, aY, ph)
        super.drawStrikingPaddle(canvas, cx, cy, aX, aY, sweet, overcharged, progress)
    }

    private fun drawHalo(canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase) {
        val half = paddleHalfLength()
        val perpX = -aY
        val perpY = aX
        val glowColor = if (ph == ChargePhase.Inert) theme.main.secondary else theme.accent.primary

        val outerAlpha: Int
        val innerAlpha: Int
        if (ph == ChargePhase.SweetSpot) {
            // Rapid flicker at a distinct frequency — neon tube effect
            val flicker = 0.5f + 0.5f * sin(frame * 0.8f)
            outerAlpha = (70 + 60 * flicker).toInt().coerceIn(0, 255)
            innerAlpha = (100 + 80 * flicker).toInt().coerceIn(0, 255)
        } else {
            outerAlpha = 70
            innerAlpha = 130
        }

        halo.color = Palette.withAlpha(glowColor, outerAlpha)
        halo.strokeWidth = Settings.strokeWidth * 3.2f
        canvas.drawLine(cx - perpX * half, cy - perpY * half, cx + perpX * half, cy + perpY * half, halo)
        halo.color = Palette.withAlpha(glowColor, innerAlpha)
        halo.strokeWidth = Settings.strokeWidth * 2.0f
        canvas.drawLine(cx - perpX * half, cy - perpY * half, cx + perpX * half, cy + perpY * half, halo)
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(NeonScar(rx, ry, -aY, aX, currentRenderer.radius, theme.accent.primary))
    }

    private class NeonScar(
        private val cx: Float, private val cy: Float,
        private val perpX: Float, private val perpY: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val len = radius * 1.5f
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 300f).coerceIn(0f, 1f)
            val alpha = (150 * (1f - t * 0.8f)).toInt().coerceIn(0, 255)
            // Outer glow
            paint.color = color
            paint.alpha = (alpha * 0.4f).toInt()
            paint.strokeWidth = radius * 0.35f
            canvas.drawLine(cx - perpX * len, cy - perpY * len, cx + perpX * len, cy + perpY * len, paint)
            // Bright inner core
            paint.alpha = alpha
            paint.strokeWidth = radius * 0.1f
            canvas.drawLine(cx - perpX * len, cy - perpY * len, cx + perpX * len, cy + perpY * len, paint)
            paint.alpha = 255
        }
    }
}
