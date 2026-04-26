package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.withSave

/**
 * Dynamite stick. Fuse lights up when the sweet spot starts. On a sweet-spot release the strike
 * animation shows an explosion at the puck; on a missed release the stick just shoves into the puck.
 */
class MetalLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val stick = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val fuse = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val spark = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rect = RectF()

    override fun drawChargingPaddle(canvas: Canvas) {
        drawStick(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        if (sweet) {
            drawExplosion(canvas, progress)
        } else {
            val ph = if (fatigued) ChargePhase.Inert else ChargePhase.Building
            drawStick(canvas, cx, cy, aX, aY, ph, if (fatigued) 0f else 1f)
        }
    }

    private fun drawStick(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        canvas.withSave {
            val angle = Math.toDegrees(kotlin.math.atan2(aY, aX).toDouble()).toFloat()
            rotate(angle + 90f, cx, cy)

            val halfLen = paddleHalfLength() * 0.9f
            val halfThick = renderer.radius * 0.28f

            stick.color = if (ph == ChargePhase.Inert) theme.main.secondary else responsiveSecondary
            rect.set(cx - halfLen, cy - halfThick, cx + halfLen, cy + halfThick)
            drawRoundRect(rect, halfThick * 0.4f, halfThick * 0.4f, stick)

            if (fill > 0f) {
                stick.color = theme.shield.primary
                stick.alpha = (220 * fill).toInt().coerceIn(0, 255)
                val bandHalf = halfLen * fill
                rect.set(cx - bandHalf, cy - halfThick * 0.6f, cx + bandHalf, cy + halfThick * 0.6f)
                drawRoundRect(rect, halfThick * 0.4f, halfThick * 0.5f, stick)
                stick.alpha = 255
            }

            val fuseBaseX = cx + halfLen
            val fuseBaseY = cy
            val fuseTipX = fuseBaseX + halfThick * 1.4f
            val fuseTipY = cy - halfThick * 1.2f
            fuse.color = Color.rgb(70, 50, 30)
            fuse.strokeWidth = Settings.strokeWidth * 0.4f
            drawLine(fuseBaseX, fuseBaseY, fuseTipX, fuseTipY, fuse)

            if (ph == ChargePhase.SweetSpot) {
                val flicker = 0.6f + 0.4f * sin(frame * 0.9f)
                spark.color = responsivePrimary
                spark.alpha = (255 * flicker).toInt().coerceIn(0, 255)
                drawCircle(fuseTipX, fuseTipY, halfThick , spark)
                spark.alpha = 255
            }
        }
    }

    private fun drawExplosion(canvas: Canvas, progress: Float) {
        val cx = renderer.x
        val cy = renderer.y
        val r = renderer.radius * (1f + progress * 2.2f)
        spark.color = Color.rgb(255, 180, 40)
        spark.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, spark)
        spark.color = Color.rgb(255, 240, 150)
        spark.alpha = (220 * (1f - progress)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 0.55f, spark)
        spark.alpha = 255
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(MetalScorch(rx, ry, renderer.radius))
    }

    private class MetalScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float
    ) : Effects.PersistentEffect {
        private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private var frame = 0
        override val isDone = false

        private class Spark(val dx: Float, val dy: Float, var alpha: Float, val fadeRate: Float)
        private val sparks: List<Spark>

        init {
            val rand = Random(cx.toLong())
            sparks = List(7) {
                val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
                val dist = radius * (0.9f + rand.nextFloat() * 1.5f)
                Spark(
                    cos(angle) * dist, sin(angle) * dist,
                    200f, 0.25f + rand.nextFloat() * 0.6f
                )
            }
        }

        override fun step() {
            frame++
            for (s in sparks) s.alpha = (s.alpha - s.fadeRate).coerceAtLeast(0f)
        }

        override fun draw(canvas: Canvas) {
            // Dark scorch mark persists
            fill.color = Color.rgb(30, 20, 10)
            fill.alpha = 160
            canvas.drawCircle(cx, cy, radius * 1.1f, fill)
            // Metallic sparks wink out
            for (s in sparks) {
                if (s.alpha <= 0f) continue
                fill.color = Color.rgb(180, 160, 100)
                fill.alpha = s.alpha.toInt().coerceIn(0, 255)
                canvas.drawCircle(cx + s.dx, cy + s.dy, radius * 0.09f, fill)
            }
        }
    }
}
