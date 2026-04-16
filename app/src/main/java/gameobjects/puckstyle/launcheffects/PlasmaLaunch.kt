package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Crackling energy orb with arcing tendrils back to the puck. */
class PlasmaLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val core = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val arc = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun drawChargingPaddle(canvas: Canvas, puck: Puck) {
        drawOrb(canvas, puck, paddleX, paddleY, phase, chargeFillRatio)
        drawArcs(canvas, puck, paddleX, paddleY, phase)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawOrb(canvas, puck, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
        drawArcs(canvas, puck, cx, cy, ph)
    }

    private fun drawOrb(canvas: Canvas, puck: Puck, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val r = puck.radius * (0.5f + 0.05f * sin(frame * 0.5f))
        val outer = if (ph == ChargePhase.Overcharged) theme.secondary else Palette.cyclingPurple(frame)
        core.color = outer
        core.alpha = 140
        canvas.drawCircle(cx, cy, r * 1.3f, core)
        core.alpha = 255
        core.color = if (ph == ChargePhase.Overcharged) theme.secondary else theme.accent
        canvas.drawCircle(cx, cy, r, core)
        if (fill > 0f) {
            core.color = android.graphics.Color.WHITE
            core.alpha = (220 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * 0.55f * fill, core)
            core.alpha = 255
        }
    }

    private fun drawArcs(canvas: Canvas, puck: Puck, cx: Float, cy: Float, ph: ChargePhase) {
        val rand = Random((frame / 2).toLong())
        val count = if (ph == ChargePhase.SweetSpot) 5 else 3
        arc.strokeWidth = Settings.strokeWidth * 0.5f
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
            val len = puck.radius * (0.6f + rand.nextFloat() * 0.6f)
            val ex = cx + cos(angle) * len
            val ey = cy + sin(angle) * len
            arc.color = if (ph == ChargePhase.Overcharged) theme.secondary else Palette.cyclingPurple(frame + i)
            arc.alpha = 200
            canvas.drawLine(cx, cy, ex, ey, arc)
        }
        arc.alpha = 255
    }

    override fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        arc.color = theme.accent
        arc.alpha = (200 * remaining).toInt().coerceIn(0, 255)
        arc.strokeWidth = Settings.strokeWidth * 0.7f
        canvas.drawCircle(rx, ry, puck.radius * (1f + (1f - remaining)), arc)
        arc.alpha = 255
    }
}
