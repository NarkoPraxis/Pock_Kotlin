package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.sin

/** Glow-stick: paddle bar with an outer halo glow. Sweet spot flickers at a distinct frequency. */
class NeonLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    // Cache stroke-width multiples — Settings.strokeWidth is fixed after setup
    private val haloStrokeOuter = Settings.strokeWidth * 3.2f
    private val haloStrokeInner = Settings.strokeWidth * 2.0f

    override fun drawChargingPaddle(scope: DrawScope) {
        drawHalo(scope, paddleX, paddleY, aimX, aimY, phase)
        super.drawChargingPaddle(scope)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawHalo(scope, cx, cy, aX, aY, ph)
        super.drawStrikingPaddle(scope, cx, cy, aX, aY, sweet, fatigued, progress)
    }

    private fun drawHalo(scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase) {
        val half = paddleHalfLength()
        val perpX = -aY
        val perpY = aX
        val glowColor = if (ph == ChargePhase.Inert) theme.inert.secondary else responsiveSecondary

        val outerAlpha: Int
        val innerAlpha: Int
        if (ph == ChargePhase.SweetSpot) {
            val flicker = 0.5f + 0.5f * sin(frame * 0.8f)
            outerAlpha = (70 + 60 * flicker).toInt().coerceIn(0, 255)
            innerAlpha = (100 + 80 * flicker).toInt().coerceIn(0, 255)
        } else {
            outerAlpha = 70
            innerAlpha = 130
        }

        val x0 = cx - perpX * half
        val y0 = cy - perpY * half
        val x1 = cx + perpX * half
        val y1 = cy + perpY * half

        scope.drawLine(
            color = Color(Palette.withAlpha(glowColor, outerAlpha)),
            start = Offset(x0, y0),
            end = Offset(x1, y1),
            strokeWidth = haloStrokeOuter,
            cap = StrokeCap.Round
        )
        scope.drawLine(
            color = Color(Palette.withAlpha(glowColor, innerAlpha)),
            start = Offset(x0, y0),
            end = Offset(x1, y1),
            strokeWidth = haloStrokeInner,
            cap = StrokeCap.Round
        )
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(NeonScar(rx, ry, -aY, aX, renderer.radius, theme.shield.primary))
    }



    private class NeonScar(
        private val cx: Float, private val cy: Float,
        private val perpX: Float, private val perpY: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val len = radius * 1.5f
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 300f).coerceIn(0f, 1f)
            val alpha = (150 * (1f - t * 0.8f)).toInt().coerceIn(100, 255)
            // Outer glow
            paint.color = color
            paint.alpha = (alpha * 0.5f).toInt()
            paint.strokeWidth = radius * 0.7f
            canvas.drawLine(cx - perpX * len, cy - perpY * len, cx + perpX * len, cy + perpY * len, paint)
            // Bright inner core
            paint.alpha = alpha
            paint.strokeWidth = radius * 0.35f
            canvas.drawLine(cx - perpX * len, cy - perpY * len, cx + perpX * len, cy + perpY * len, paint)
            paint.alpha = 255
        }
    }
}
