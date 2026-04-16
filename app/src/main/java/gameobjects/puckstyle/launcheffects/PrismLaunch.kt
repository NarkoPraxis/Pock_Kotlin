package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette

/** Triangular prism. Sweet spot refracts rainbow streaks across the paddle. */
class PrismLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val edge = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
        strokeWidth = Settings.strokeWidth * 0.6f; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun drawChargingPaddle(canvas: Canvas, puck: Puck) =
        drawPrism(canvas, puck, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawPrism(canvas, puck, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
        if (sweet) drawRefraction(canvas, puck, cx, cy, aX, aY, progress)
    }

    private fun drawPrism(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, ratio: Float
    ) {
        val half = paddleHalfLength(puck) * 0.85f
        val depth = puck.radius * 0.5f
        val pX = -aY
        val pY = aX
        path.reset()
        path.moveTo(cx + pX * half, cy + pY * half)
        path.lineTo(cx - pX * half, cy - pY * half)
        path.lineTo(cx - aX * depth, cy - aY * depth)
        path.close()

        fill.color = if (ph == ChargePhase.Overcharged) theme.secondary else Color.WHITE
        fill.alpha = 200
        canvas.drawPath(path, fill)
        if (ratio > 0f) {
            fill.color = Palette.cyclingHue(frame, 4f)
            fill.alpha = (180 * ratio).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fill)
        }
        fill.alpha = 255
        edge.color = theme.secondary
        canvas.drawPath(path, edge)
    }

    private fun drawRefraction(canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float, progress: Float) {
        val pX = -aY
        val pY = aX
        val half = paddleHalfLength(puck)
        for (i in 0 until 6) {
            val offset = (i - 2.5f) / 2.5f * half
            edge.color = Palette.hsv(i * 60f + frame * 3f, 1f, 1f)
            edge.alpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawLine(
                cx + pX * offset, cy + pY * offset,
                puck.x + pX * offset * 0.6f, puck.y + pY * offset * 0.6f,
                edge
            )
        }
        edge.alpha = 255
    }

    override fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        for (i in 0 until 6) {
            edge.color = Palette.hsv(i * 60f + frame * 2f, 1f, 1f)
            edge.alpha = (160 * remaining).toInt().coerceIn(0, 255)
            canvas.drawCircle(rx, ry, puck.radius * (0.9f + i * 0.15f) * (1f + (1f - remaining) * 0.4f), edge)
        }
        edge.alpha = 255
    }
}
