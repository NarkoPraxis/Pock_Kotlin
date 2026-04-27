package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.cos
import kotlin.math.sin

/** Ice shard: diamond-shape perpendicular to aim. Sweet-spot leaves a frost mark that evaporates to a water puddle. */
class IceLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val shardFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val shardStroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    override fun drawChargingPaddle(canvas: Canvas) {
        drawShard(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawShard(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
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

        shardFill.color = responsivePrimary
        canvas.drawPath(path, shardFill)

        if (fill > 0f) {
            shardFill.color = theme.shield.primary
            shardFill.alpha = (180 * fill).toInt().coerceIn(0, 255)
            canvas.drawPath(path, shardFill)
            shardFill.alpha = 255
        }

        shardStroke.color = Color.WHITE
        shardStroke.strokeWidth = Settings.strokeWidth * 0.6f
        canvas.drawPath(path, shardStroke)
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(IcePuddle(rx, ry, renderer.radius, theme))
    }

    companion object {
        fun spawnImpact(cx: Float, cy: Float, radius: Float, theme: ColorTheme) {
            Effects.addPersistentEffect(IcePuddle(cx, cy, radius, theme))
        }
    }

    private class IcePuddle(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val theme: ColorTheme
    ) : Effects.PersistentEffect {
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val crystalPath = Path()
        private var frame = 0

        private var puddleExists = true
        private val evaporateDuration = 120f

        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val uncappedTime = (frame / evaporateDuration)
            val cappedTime = uncappedTime.coerceIn(0f, 1f)
            fill.color = theme.main.primary
            fill.alpha = (120 - (60 * uncappedTime)).toInt().coerceIn(0, 255)

            if (fill.alpha > 0) {
                canvas.drawCircle(cx, cy, radius * uncappedTime * 1.5f, fill)
            }

            drawCrystal(canvas, cappedTime)
        }

        private fun drawCrystal(canvas: Canvas, t: Float) {
            val crystalR = radius * (1.4f - t * 1.1f)
            crystalPath.reset()
            val pts = 8
            for (i in 0 until pts) {
                val angle = (i * 360.0 / pts * Math.PI / 180.0).toFloat()
                val outerR = crystalR * (if (i % 2 == 0) 2.3f else 1f)
                val x = cx + cos(angle) * outerR
                val y = cy + sin(angle) * outerR
                if (i == 0) crystalPath.moveTo(x, y) else crystalPath.lineTo(x, y)
            }
            crystalPath.close()
            fill.color = Color.WHITE
            fill.alpha = 255
            canvas.drawPath(crystalPath, fill)
            stroke.color = theme.main.primary
            stroke.alpha = 130
            stroke.strokeWidth = Settings.strokeWidth * 0.5f
            canvas.drawPath(crystalPath, stroke)
        }
    }
}
