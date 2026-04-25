package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import utility.Effects
import kotlin.math.sin
import kotlin.random.Random

/** Translucent ghost-puck double trailing behind the real one. */
class GhostLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val body = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val outline = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = Settings.strokeWidth * 0.8f
    }

    override fun drawChargingPaddle(canvas: Canvas) =
        drawGhost(canvas, paddleX, paddleY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Inert else ChargePhase.Building
        drawGhost(canvas, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawGhost(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val r = currentRenderer.radius * (0.75f + 0.05f * sin(frame * 0.2f))
        val base = if (ph == ChargePhase.Inert) theme.main.secondary else theme.main.primary
        body.color = base
        body.alpha = 90
        canvas.drawCircle(cx, cy, r, body)
        outline.color = base
        outline.alpha = 160
        canvas.drawCircle(cx, cy, r, outline)

        if (fill > 0f) {
            body.color = theme.effect.primary
            body.alpha = (150 * fill).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * fill, body)
        }
        body.alpha = 255
        outline.alpha = 255
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(EctoplasmMark(rx, ry, currentRenderer.radius, theme.effect.primary))
    }

    private class EctoplasmMark(
        cx: Float, cy: Float,
        radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val blobPts: Array<FloatArray>
        private var frame = 0
        override val isDone = false

        init {
            val rand = Random(cx.toLong() xor cy.toLong())
            blobPts = Array(5) {
                floatArrayOf(
                    cx + (rand.nextFloat() - 0.5f) * radius * 1.4f,
                    cy + (rand.nextFloat() - 0.5f) * radius * 1.4f,
                    radius * (0.35f + rand.nextFloat() * 0.4f)
                )
            }
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(frame * 0.04f))
            val alpha = (85 * pulse).toInt().coerceIn(0, 255)
            paint.color = color
            paint.alpha = alpha
            for (b in blobPts) canvas.drawCircle(b[0], b[1], b[2], paint)
            paint.alpha = 255
        }
    }
}
