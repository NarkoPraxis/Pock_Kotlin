package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import utility.Effects
import kotlin.math.sin

/** Tethered fireball: a mini flame instead of a paddle bar. Sweet-spot leaves a persistent scorch. */
class FireLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val flamePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawFireball(canvas, paddleX, paddleY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
        drawFireball(canvas, cx, cy, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawFireball(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val base = currentRenderer.radius * 0.6f
        val jitter = 1f + 0.08f * sin(frame * 0.9f)
        val outerR = base * jitter

        val outerColor = when (ph) {
            ChargePhase.Overcharged -> theme.secondary
            else -> theme.primary
        }
        flamePaint.color = outerColor
        flamePaint.alpha = 255
        canvas.drawCircle(cx, cy, outerR, flamePaint)

        if (fill > 0f) {
            val coreColor = if (ph == ChargePhase.SweetSpot) theme.accent else theme.secondary
            flamePaint.color = coreColor
            val pulse = if (ph == ChargePhase.SweetSpot) 0.8f + 0.2f * sin(frame * 0.4f) else 1f
            flamePaint.alpha = (255 * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, outerR * 0.6f * fill, flamePaint)
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(FireScorch(rx, ry, currentRenderer.radius, theme.primary))
    }

    override fun paddleHalfLength(): Float = currentRenderer.radius * 0.6f
    override fun paddleThickness(): Float = Settings.strokeWidth

    private class FireScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val emberColor: Int
    ) : Effects.PersistentEffect {
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val glow = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            // Ember glow fades over ~3 seconds (180 frames)
            if (frame < 180) {
                val glowAlpha = (100 * (1f - frame / 180f)).toInt().coerceIn(0, 255)
                glow.color = emberColor
                glow.alpha = glowAlpha
                glow.strokeWidth = radius * 0.4f
                canvas.drawCircle(cx, cy, radius * 1.2f, glow)
            }
            // Dark char mark persists
            fill.color = Color.rgb(40, 20, 10)
            fill.alpha = 160
            canvas.drawCircle(cx, cy, radius * 0.9f, fill)
        }
    }
}
