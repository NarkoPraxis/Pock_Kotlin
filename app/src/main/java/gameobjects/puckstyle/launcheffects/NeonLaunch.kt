package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette

/** Glow-stick: paddle bar with an outer halo glow. */
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
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawHalo(canvas, cx, cy, aX, aY, ph)
        super.drawStrikingPaddle(canvas, cx, cy, aX, aY, sweet, overcharged, progress)
    }

    private fun drawHalo(canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase) {
        val half = paddleHalfLength()
        val perpX = -aY
        val perpY = aX
        val glowColor = if (ph == ChargePhase.Overcharged) theme.secondary else theme.accent
        halo.color = Palette.withAlpha(glowColor, 70)
        halo.strokeWidth = Settings.strokeWidth * 3.2f
        canvas.drawLine(cx - perpX * half, cy - perpY * half, cx + perpX * half, cy + perpY * half, halo)
        halo.color = Palette.withAlpha(glowColor, 130)
        halo.strokeWidth = Settings.strokeWidth * 2.0f
        canvas.drawLine(cx - perpX * half, cy - perpY * half, cx + perpX * half, cy + perpY * half, halo)
    }
}
