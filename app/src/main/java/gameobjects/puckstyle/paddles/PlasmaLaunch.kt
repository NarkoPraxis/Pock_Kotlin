package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import utility.Effects
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

    override fun drawChargingPaddle(canvas: Canvas) {
        drawOrb(canvas, paddleX, paddleY, phase, chargeFillRatio)
        drawArcs(canvas, paddleX, paddleY, phase)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawOrb(canvas, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
        drawArcs(canvas, cx, cy, ph)
    }

    private fun drawOrb(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val r = currentRenderer.radius * (0.5f + 0.05f * sin(frame * 0.5f))
        val outer = if (ph == ChargePhase.Overcharged) theme.secondary else theme.accent
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

    private fun drawArcs(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase) {
        val rand = Random((frame / 2).toLong())
        val count = if (ph == ChargePhase.SweetSpot) 5 else 3
        arc.strokeWidth = Settings.strokeWidth * 0.5f
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
            val len = currentRenderer.radius * (0.6f + rand.nextFloat() * 0.6f)
            val ex = cx + cos(angle) * len
            val ey = cy + sin(angle) * len
            arc.color = if (ph == ChargePhase.Overcharged) theme.secondary else theme.accent
            arc.alpha = 200
            canvas.drawLine(cx, cy, ex, ey, arc)
        }
        arc.alpha = 255
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(PlasmaBurn(rx, ry, currentRenderer.radius, theme.accent))
    }

    private class PlasmaBurn(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val arc = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private var frame = 0
        override val isDone = false

        private val arcAngles: FloatArray
        init {
            val rand = Random(cx.toLong())
            arcAngles = FloatArray(4) { rand.nextFloat() * 2f * Math.PI.toFloat() }
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            // Central scorch mark persists
            fill.color = Color.rgb(20, 10, 20)
            fill.alpha = 180
            canvas.drawCircle(cx, cy, radius * 0.7f, fill)
            // Arc tendrils shrink and fade over ~3 seconds
            val arcT = (frame / 180f).coerceIn(0f, 1f)
            val arcAlpha = (160 * (1f - arcT)).toInt().coerceIn(0, 255)
            if (arcAlpha > 0) {
                arc.color = color
                arc.alpha = arcAlpha
                arc.strokeWidth = Settings.strokeWidth * 0.4f
                val arcLen = radius * (1.5f - arcT * 1f)
                for (angle in arcAngles) {
                    canvas.drawLine(cx, cy, cx + cos(angle) * arcLen, cy + sin(angle) * arcLen, arc)
                }
            }
            arc.alpha = 255
        }
    }
}
