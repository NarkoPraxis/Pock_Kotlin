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

/** Ice shard: diamond-shape perpendicular to aim. Sweet-spot leaves a frost puff. */
class IceLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val shardFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val shardStroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    override fun drawChargingPaddle(canvas: Canvas, puck: Puck) {
        drawShard(canvas, puck, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawShard(canvas, puck, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawShard(canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase, fill: Float) {
        val half = paddleHalfLength(puck)
        val pX = -aY
        val pY = aX
        val longR = half
        val shortR = half * 0.45f

        path.reset()
        path.moveTo(cx + pX * longR, cy + pY * longR)
        path.lineTo(cx + aX * shortR, cy + aY * shortR)
        path.lineTo(cx - pX * longR, cy - pY * longR)
        path.lineTo(cx - aX * shortR, cy - aY * shortR)
        path.close()

        val base = if (ph == ChargePhase.Overcharged) theme.secondary else Color.rgb(180, 220, 255)
        shardFill.color = base
        canvas.drawPath(path, shardFill)

        if (fill > 0f) {
            shardFill.color = theme.accent
            shardFill.alpha = (180 * fill).toInt().coerceIn(0, 255)
            canvas.drawPath(path, shardFill)
            shardFill.alpha = 255
        }

        shardStroke.color = Color.WHITE
        shardStroke.strokeWidth = Settings.strokeWidth * 0.6f
        canvas.drawPath(path, shardStroke)
    }

    override fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        shardFill.color = Color.rgb(220, 240, 255)
        shardFill.alpha = (160 * remaining).toInt().coerceIn(0, 255)
        canvas.drawCircle(rx, ry, puck.radius * (1f + (1f - remaining) * 0.6f), shardFill)
    }
}
