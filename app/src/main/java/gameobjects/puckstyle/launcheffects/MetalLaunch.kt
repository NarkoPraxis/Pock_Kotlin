package gameobjects.puckstyle.launcheffects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dynamite stick. Fuse lights up when the sweet spot starts. On a sweet-spot release the strike
 * animation shows an explosion at the puck; on a missed release the stick just shoves into the puck.
 */
class MetalLaunch(theme: ColorTheme) : PaddleLaunchEffect(theme) {

    private val stick = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val fuse = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val spark = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rect = RectF()

    override fun drawChargingPaddle(canvas: Canvas, puck: Puck) {
        drawStick(canvas, puck, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        if (sweet) {
            drawExplosion(canvas, puck, progress)
        } else {
            val ph = if (overcharged) ChargePhase.Overcharged else ChargePhase.Building
            drawStick(canvas, puck, cx, cy, aX, aY, ph, if (overcharged) 0f else 1f)
        }
    }

    private fun drawStick(
        canvas: Canvas, puck: Puck, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        canvas.save()
        val angle = Math.toDegrees(kotlin.math.atan2(aY, aX).toDouble()).toFloat()
        canvas.rotate(angle, cx, cy)

        val halfLen = paddleHalfLength(puck) * 0.9f
        val halfThick = puck.radius * 0.28f

        // body (red or theme-locked)
        stick.color = if (ph == ChargePhase.Overcharged) theme.secondary else Color.rgb(180, 40, 30)
        rect.set(cx - halfLen, cy - halfThick, cx + halfLen, cy + halfThick)
        canvas.drawRoundRect(rect, halfThick * 0.4f, halfThick * 0.4f, stick)

        // accent band in the middle during charge fill
        if (fill > 0f) {
            stick.color = theme.accent
            stick.alpha = (220 * fill).toInt().coerceIn(0, 255)
            val bandHalf = halfLen * fill
            rect.set(cx - bandHalf, cy - halfThick * 0.6f, cx + bandHalf, cy + halfThick * 0.6f)
            canvas.drawRoundRect(rect, halfThick * 0.4f, halfThick * 0.4f, stick)
            stick.alpha = 255
        }

        // fuse at the far end (toward the perpendicular, pointing "up" in rotated space)
        val fuseBaseX = cx + halfLen
        val fuseBaseY = cy
        val fuseTipX = fuseBaseX + halfThick * 1.4f
        val fuseTipY = cy - halfThick * 1.2f
        fuse.color = Color.rgb(70, 50, 30)
        fuse.strokeWidth = Settings.strokeWidth * 0.4f
        canvas.drawLine(fuseBaseX, fuseBaseY, fuseTipX, fuseTipY, fuse)

        if (ph == ChargePhase.SweetSpot) {
            val flicker = 0.6f + 0.4f * sin(frame * 0.9f)
            spark.color = Color.rgb(255, 220, 100)
            spark.alpha = (255 * flicker).toInt().coerceIn(0, 255)
            canvas.drawCircle(fuseTipX, fuseTipY, halfThick * 0.55f, spark)
            spark.alpha = 255
        }
        canvas.restore()
    }

    private fun drawExplosion(canvas: Canvas, puck: Puck, progress: Float) {
        val cx = puck.x
        val cy = puck.y
        val r = puck.radius * (1f + progress * 2.2f)
        spark.color = Color.rgb(255, 180, 40)
        spark.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, spark)
        spark.color = Color.rgb(255, 240, 150)
        spark.alpha = (220 * (1f - progress)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 0.55f, spark)
        spark.alpha = 255
    }

    override fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        val rand = Random(puck.frame.toLong())
        spark.color = Color.rgb(30, 20, 10)
        spark.alpha = (200 * remaining).toInt().coerceIn(0, 255)
        canvas.drawCircle(rx, ry, puck.radius * (1.1f + (1f - remaining) * 0.5f), spark)
        // a few debris dots
        for (i in 0 until 5) {
            val dx = (rand.nextFloat() - 0.5f) * puck.radius * 3f
            val dy = (rand.nextFloat() - 0.5f) * puck.radius * 3f
            canvas.drawCircle(rx + dx, ry + dy, puck.radius * 0.12f, spark)
        }
        spark.alpha = 255
    }
}
