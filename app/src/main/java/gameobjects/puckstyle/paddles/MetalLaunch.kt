package gameobjects.puckstyle.paddles

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import gameobjects.puckstyle.paddles.MetalLaunch.MetalScorch.Spark
import kotlin.math.PI

/**
 * Dynamite stick. Fuse lights up when the sweet spot starts. On a sweet-spot release the strike
 * animation shows an explosion at the puck; on a missed release the stick just shoves into the puck.
 */
class MetalLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val stick = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val fuse = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        color = Color.rgb(70, 50, 30)
        strokeWidth = Settings.strokeWidth * 0.4f
    }
    private val spark = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rect = RectF()

    // Constant colors used in drawExplosion — cached to avoid Color.rgb() per frame
    private val explosionOuter = Color.rgb(255, 180, 40)
    private val explosionInner = Color.rgb(255, 240, 150)

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

            val halfLen = paddleHalfLength() * 0.8f
            val halfThick = renderer.radius * 0.25f

            stick.color = if (ph == ChargePhase.Inert) responsivePrimary else responsiveSecondary
            rect.set(cx - halfLen, cy - halfThick, cx + halfLen, cy + halfThick)
            drawRoundRect(rect, halfThick * 0.4f, halfThick * 0.4f, stick)

            if (fill > 0f) {
                stick.color = theme.shield.primary
                val bandHalf = (halfLen * fill).coerceAtMost(halfLen - halfLen * .1f)
                rect.set(cx - bandHalf, cy - halfThick * 0.6f, cx + bandHalf, cy + halfThick * 0.6f)
                drawRoundRect(rect, halfThick, halfThick, stick)
            }

            val fuseBaseX = cx + halfLen
            val fuseBaseY = cy
            val fuseTipX = fuseBaseX + halfThick * 1.4f
            val fuseTipY = cy - halfThick * -1.2f
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
        val r = renderer.radius * (1f + progress * 5f)
        spark.color = explosionOuter
        spark.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, spark)
        spark.color = explosionInner
        spark.alpha = (220 * (1f - progress)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 0.55f, spark)
        spark.alpha = 255
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(BlastScorch(rx, ry, renderer.radius, theme.main.primary))
    }

    internal class BlastScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int,
    ) : Effects.PersistentEffect {

        private class Spark(val dx: Float, val dy: Float, var alpha: Float, val fadeRate: Float)
        private val sparks: List<Spark>
        private val spikePaths: List<Path>
        // Dedicated paint for ember dots — no shader, no blur
        private val emberPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        // Dedicated paint for spike paths — carries the blur mask and the pre-built gradient shader
        private val spikePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(radius * 0.1f, BlurMaskFilter.Blur.NORMAL)
            alpha = 50
        }
        private var frame = 0
        override val isDone = false

        init {
            val rng = Random(cx.toInt() xor cy.toInt())

            // Build starburst: 12 thin triangular spikes radiating from center.
            // Each spike is a narrow triangle: two base points very close to center
            // at ±halfWidth from the spike axis, tip at the outer radius.
            val spikeCount = 12
            // Pre-computed irregular length multipliers so the silhouette is organic.
            // Lengths alternate between longer and shorter with added per-spike noise.
            val lengthPattern = floatArrayOf(
                1.50f, 0.72f, 1.60f, 0.60f, 1.2f, 0.85f, 1.70f, 0.55f,
                1.90f, 0.68f, 1.2f, 0.78f
            )
            spikePaths = List(spikeCount) { i ->
                val baseAngle = (i.toFloat() / spikeCount) * 2f * PI.toFloat() +  (rng.nextFloat() - 0.5f) * (2f * PI.toFloat() / spikeCount) * 1f
                val len = radius * lengthPattern[i] * (0.90f + rng.nextFloat() * 0.40f)
                // Half-angle of the spike's triangular cross-section — very narrow
                val halfWidth = radius * (0.1f + rng.nextFloat())
                val perpAngle = baseAngle + (PI / 2f).toFloat()

                val baseX1 = cx + cos(perpAngle) * halfWidth
                val baseY1 = cy + sin(perpAngle) * halfWidth
                val baseX2 = cx - cos(perpAngle) * halfWidth
                val baseY2 = cy - sin(perpAngle) * halfWidth
                val tipX = cx + cos(baseAngle) * len
                val tipY = cy + sin(baseAngle) * len

                Path().apply {
                    moveTo(baseX1, baseY1)
                    lineTo(tipX, tipY)
                    lineTo(baseX2, baseY2)
                    close()
                }
            }

            val rand = Random(cx.toLong())
            sparks = List((5..15).random()) {
                val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
                val dist = radius * (2f + rand.nextFloat() * 2f)
                Spark(
                    cos(angle) * dist, sin(angle) * dist,
                    200f, 0.25f + rand.nextFloat() * 0.6f
                )
            }

            // Build the starburst gradient once — it is fully constant after construction
            spikePaint.shader = RadialGradient(
                cx, cy,
                radius * 2f,
                intArrayOf(Color.BLACK, Color.DKGRAY, primary, Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, .7f, .8f),
                Shader.TileMode.MIRROR
            )
            emberPaint.color = primary
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            for (s in sparks) {
                if (s.alpha <= 0f) continue
                emberPaint.alpha = s.alpha.toInt().coerceIn(0, 255)
                canvas.drawCircle(cx + s.dx, cy + s.dy, radius * 0.09f, emberPaint)
            }

            // Starburst char mark: radial gradient from opaque black center to transparent tip.
            // The gradient + blur together ensure no hard edges anywhere on the spikes.
            for (path in spikePaths) {
                canvas.drawPath(path, spikePaint)
            }
        }
    }

    private class MetalScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float
    ) : Effects.PersistentEffect {
        private val scorchPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.FILL
            color = Color.rgb(30, 20, 10); alpha = 160
        }
        private val sparkPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.FILL
            color = Color.rgb(180, 160, 100)
        }
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
            canvas.drawCircle(cx, cy, radius * 1.1f, scorchPaint)
            // Metallic sparks wink out
            for (s in sparks) {
                if (s.alpha <= 0f) continue
                sparkPaint.alpha = s.alpha.toInt().coerceIn(0, 255)
                canvas.drawCircle(cx + s.dx, cy + s.dy, radius * 0.09f, sparkPaint)
            }
        }
    }
}
