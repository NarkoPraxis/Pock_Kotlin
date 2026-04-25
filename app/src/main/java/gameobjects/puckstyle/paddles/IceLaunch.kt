package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import utility.Effects
import kotlin.math.cos
import kotlin.math.sin

/** Ice shard: diamond-shape perpendicular to aim. Sweet-spot leaves a frost mark that evaporates to a water puddle. */
class IceLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val shardFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val shardStroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    override fun drawChargingPaddle(canvas: Canvas) {
        drawShard(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Inert else ChargePhase.Building
        drawShard(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawShard(canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase, fill: Float) {
        val half = paddleHalfLength()
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

        val base = if (ph == ChargePhase.Inert) theme.main.secondary else Color.rgb(180, 220, 255)
        shardFill.color = base
        canvas.drawPath(path, shardFill)

        if (fill > 0f) {
            shardFill.color = theme.effect.primary
            shardFill.alpha = (180 * fill).toInt().coerceIn(0, 255)
            canvas.drawPath(path, shardFill)
            shardFill.alpha = 255
        }

        shardStroke.color = Color.WHITE
        shardStroke.strokeWidth = Settings.strokeWidth * 0.6f
        canvas.drawPath(path, shardStroke)
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(IcePuddle(rx, ry, currentRenderer.radius, theme))
    }

    private class IcePuddle(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val theme: ColorTheme
    ) : Effects.PersistentEffect {
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val crystalPath = Path()
        private var frame = 0
        private val evaporateDuration = 120f
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            if (frame < evaporateDuration) {
                val t = frame / evaporateDuration
                val crystalR = radius * (1.2f - t * 0.7f)
                val alpha = (160 * (1f - t * 0.5f)).toInt().coerceIn(0, 255)
                drawCrystal(canvas, crystalR, alpha)
                // Water puddle appears as crystal evaporates
                fill.color = theme.main.primary
                fill.alpha = (70 * t).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, radius * 0.5f * t, fill)
            } else {
                // Water puddle persists at low opacity until goal clears it
                fill.color = theme.main.secondary
                fill.alpha = 55
                canvas.drawCircle(cx, cy, radius * 0.5f, fill)
                fill.color = theme.main.primary
                fill.alpha = 40
                canvas.drawCircle(cx, cy, radius * 0.3f, fill)
            }
        }

        private fun drawCrystal(canvas: Canvas, r: Float, alpha: Int) {
            crystalPath.reset()
            val pts = 8
            for (i in 0 until pts) {
                val angle = (i * 360.0 / pts * Math.PI / 180.0).toFloat()
                val outerR = r * (if (i % 2 == 0) 1f else 0.55f)
                val x = cx + cos(angle) * outerR
                val y = cy + sin(angle) * outerR
                if (i == 0) crystalPath.moveTo(x, y) else crystalPath.lineTo(x, y)
            }
            crystalPath.close()
            fill.color = Color.rgb(220, 240, 255)
            fill.alpha = (alpha * 0.65f).toInt()
            canvas.drawPath(crystalPath, fill)
            stroke.color = Color.WHITE
            stroke.alpha = alpha
            stroke.strokeWidth = Settings.strokeWidth * 0.5f
            canvas.drawPath(crystalPath, stroke)
        }
    }
}
