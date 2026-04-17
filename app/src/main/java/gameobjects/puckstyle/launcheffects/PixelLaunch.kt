package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect

/** Chunky 8-bit brick paddle. Charge fills as discrete pixel segments. */
class PixelLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {
    private val block = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val rect = RectF()

    override fun drawChargingPaddle(canvas: Canvas) =
        drawPixelBar(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawPixelBar(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawPixelBar(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        canvas.save()
        val angle = Math.toDegrees(kotlin.math.atan2(aY, aX).toDouble()).toFloat()
        canvas.rotate(angle + 90f, cx, cy)

        val totalLen = paddleHalfLength() * 2f
        val thick = currentRenderer.radius * 0.45f
        val cells = 6
        val cellW = totalLen / cells
        val startX = cx - totalLen / 2f

        val base = theme.secondary
        val fillColor = theme.accent
        val filledCells = (cells * fill).toInt()
        val center = cells / 2

        for (i in 0 until cells) {
            rect.set(startX + i * cellW + 1f, cy - thick, startX + (i + 1) * cellW - 1f, cy + thick)
            val dist = kotlin.math.abs(i - (center - 0.5f)).toInt()
            val isFilled = ph == ChargePhase.SweetSpot || dist < filledCells
            block.color = if (isFilled && ph != ChargePhase.Overcharged) fillColor else base
            canvas.drawRect(rect, block)
        }
        canvas.restore()
    }

    override fun drawResidual(canvas: Canvas, rx: Float, ry: Float, remaining: Float) {
        block.color = theme.accent
        block.alpha = (220 * remaining).toInt().coerceIn(0, 255)
        val r = currentRenderer.radius * (0.8f + (1f - remaining) * 0.6f)
        rect.set(rx - r, ry - r, rx + r, ry + r)
        canvas.drawRect(rect, block)
        block.alpha = 255
    }

    override fun paddleThickness(): Float = Settings.strokeWidth * 1.6f
}
