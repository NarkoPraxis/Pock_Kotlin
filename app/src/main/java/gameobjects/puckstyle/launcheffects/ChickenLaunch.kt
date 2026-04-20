package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.EggSplat
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import kotlin.math.atan2
import kotlin.math.sin

class ChickenLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val eggPaint = Paint().apply { isAntiAlias = true }
    private var independentSplat: EggSplat? = null

    override fun draw(canvas: Canvas, renderer: PuckRenderer) {
        super.draw(canvas, renderer)
        val s = independentSplat ?: return
        s.step()
        s.draw(canvas, eggPaint)
        if (s.isDone) independentSplat = null
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawEgg(canvas, paddleX, paddleY, chargeFillRatio, phase)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = when {
            sweet       -> ChargePhase.SweetSpot
            overcharged -> ChargePhase.Overcharged
            else        -> ChargePhase.Building
        }
        drawEgg(canvas, cx, cy, if (sweet || !overcharged) 1f else 0f, ph)
    }

    private fun drawEgg(canvas: Canvas, cx: Float, cy: Float, fillRatio: Float, ph: ChargePhase) {
        val r = currentRenderer.radius
        val eggW = r * 0.5f
        val eggH = r * 0.7f
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f

        val angle = Math.toDegrees(atan2(aimY.toDouble(), aimX.toDouble())).toFloat()
        canvas.save()
        canvas.rotate(angle + 90f, cx, cy)

        // White filled base — no outline
        eggPaint.style = Paint.Style.FILL
        eggPaint.color = Color.WHITE
        canvas.drawOval(cx - eggW, cy - eggH, cx + eggW, cy + eggH, eggPaint)

        // Center-out purple charge fill on top
        if (fillRatio > 0f && ph != ChargePhase.Overcharged) {
            eggPaint.color = Palette.withAlpha(theme.accent, (220 * pulse).toInt())
            canvas.drawOval(cx - eggW * fillRatio, cy - eggH * fillRatio,
                            cx + eggW * fillRatio, cy + eggH * fillRatio, eggPaint)
        }

        canvas.restore()
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        independentSplat = EggSplat(rx, ry, currentRenderer.radius, theme)
    }

    override fun drawResidual(canvas: Canvas, rx: Float, ry: Float, remaining: Float) {
        // Handled independently in draw()
    }

    override fun reset() {
        super.reset()
        independentSplat = null
    }
}
