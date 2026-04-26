package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.EggSplat
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import utility.Effects
import kotlin.math.atan2
import kotlin.math.sin
import androidx.core.graphics.withRotation

class ChickenLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val eggPaint = Paint().apply { isAntiAlias = true }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawEgg(canvas, paddleX, paddleY, chargeFillRatio, phase)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = when {
            sweet       -> ChargePhase.SweetSpot
            fatigued -> ChargePhase.Inert
            else        -> ChargePhase.Building
        }
        drawEgg(canvas, cx, cy, if (sweet || !fatigued) 1f else 0f, ph)
    }

    private fun drawEgg(canvas: Canvas, cx: Float, cy: Float, fillRatio: Float, ph: ChargePhase) {
        val r = renderer.radius
        val eggW = r * 0.5f
        val eggH = r * 0.7f
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f
        val colors = resolvedColors(renderer)

        val angle = Math.toDegrees(atan2(aimY.toDouble(), aimX.toDouble())).toFloat()
        canvas.withRotation(angle + 90f, cx, cy) {
            eggPaint.style = Paint.Style.FILL
            eggPaint.color = if (renderer.isInert) colors.primary else android.graphics.Color.WHITE
            drawOval(cx - eggW, cy - eggH, cx + eggW, cy + eggH, eggPaint)

            if (fillRatio > 0f && ph != ChargePhase.Inert) {
                eggPaint.color = Palette.withAlpha(theme.effect.primary, (220 * pulse).toInt())
                drawOval(
                    cx - eggW * fillRatio, cy - eggH * fillRatio,
                    cx + eggW * fillRatio, cy + eggH * fillRatio, eggPaint
                )
            }

        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(ChickenPersistentEffect(EggSplat(rx, ry, renderer.radius, theme)))
    }

    private class ChickenPersistentEffect(private val splat: EggSplat) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true }
        override val isDone get() = splat.isDone
        override fun step() { splat.step() }
        override fun draw(canvas: Canvas) { splat.draw(canvas, paint) }
    }
}
