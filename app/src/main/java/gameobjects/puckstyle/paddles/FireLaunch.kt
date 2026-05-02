package gameobjects.puckstyle.paddles

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Tethered fireball: a mini flame instead of a paddle bar. Sweet-spot leaves a persistent scorch. */
class FireLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val flamePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val tailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float
    )

    val SPAWN_JITTER get() = renderer.radius * 0.35f
    val SPARK_BASE_SIZE get() = renderer.radius * .32f
    val BASE_SIZE get() = renderer.radius * .6f


    private val tailSparks = ArrayDeque<Spark>()

    override fun drawChargingPaddle(canvas: Canvas) {
        updateAndDrawTail(canvas, paddleX, paddleY)
        drawFireball(canvas, paddleX, paddleY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawFireball(canvas, cx, cy, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
    }

    private fun updateAndDrawTail(canvas: Canvas, cx: Float, cy: Float) {
        val dx = cx - renderer.x
        val dy = cy - renderer.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = dx / dist
        val ny = dy / dist

        repeat(2) {
            val speed = Random.nextFloat() * 1.2f + 0.4f
            val perpAmount = (Random.nextFloat() - 0.5f) * speed * 0.7f
            tailSparks.addLast(Spark(
                cx + (Random.nextFloat() - 0.5f) * SPAWN_JITTER * 2f,
                cy + (Random.nextFloat() - 0.5f) * SPAWN_JITTER * 2f,
                nx * speed + (-ny) * perpAmount,
                ny * speed + nx * perpAmount,
                1f
            ))
        }
        while (tailSparks.size > 24) tailSparks.removeFirst()

        // Hoist color resolution out of the loop — same value for every spark this frame.
        val primary = responsivePrimary
        val secondary = responsiveSecondary

        var i = 0
        while (i < tailSparks.size) {
            val s = tailSparks[i]
            s.x += s.vx
            s.y += s.vy
            s.life -= 0.065f
            if (s.life <= 0f) {
                tailSparks.removeAt(i)
                continue
            }
            val c = Palette.lerpColor(secondary, primary, 1f - s.life)
            tailPaint.color = Palette.withAlpha(c, (220f * s.life).toInt().coerceIn(0, 255))
            canvas.drawCircle(s.x, s.y, (SPARK_BASE_SIZE * s.life).coerceAtLeast(1f), tailPaint)
            i++
        }
    }

    private fun drawFireball(canvas: Canvas, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        val jitter = 1f + 0.08f * sin(frame * 0.9f)
        val outerR = BASE_SIZE * jitter

        flamePaint.color = responsiveSecondary
        flamePaint.alpha = 255
        canvas.drawCircle(cx, cy, outerR, flamePaint)

        if (fill > 0f) {
            val coreColor = if (ph == ChargePhase.SweetSpot) theme.shield.primary else responsivePrimary
            flamePaint.color = coreColor
            val pulse = if (ph == ChargePhase.SweetSpot) 0.8f + 0.2f * sin(frame * 0.4f) else 1f
            flamePaint.alpha = (255 * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, outerR * 0.6f * fill, flamePaint)
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        spawnFireImpact(rx, ry, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.secondary)
    }

    override fun paddleHalfLength(): Float = renderer.radius * 0.6f
    override fun paddleThickness(): Float = Settings.strokeWidth

    companion object {
        fun spawnFireImpact(cx: Float, cy: Float, radius: Float, primary: Int, secondary: Int, grey: Int) {
            Effects.addPersistentEffect(FireScorch(cx, cy, radius, primary, grey))
            Effects.addPersistentEffect(FireSparkBurst(cx, cy, radius, secondary))
        }

        fun spawnFireCelebration(cx: Float, cy: Float, radius: Float, secondary: Int, highGoal: Boolean, fullCircle: Boolean) {
            Effects.addPersistentEffect(FireSparkBurst(cx, cy, radius, secondary))
        }
    }

    private class FireSparkBurst(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val color: Int
        ) : Effects.PersistentEffect {
        private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

        private val sparks: List<Spark>
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private var frame = 0
        private val totalFrames = 60
        private val invTotalFrames = 1f / totalFrames
        private val gravity = 0.04f * radius / Settings.screenRatio
        private val sparkBaseRadius = radius * 0.8f
        override var isDone = false
            private set

        init {
            val count =  28
            val angleRange =  2f * PI.toFloat()
            sparks = List(count) { i ->
                val angle = (i.toFloat() / count) * angleRange + Random.nextFloat() * 0.4f
                val speed = radius * (0.12f + Random.nextFloat() * 0.18f)
                Spark(cx, cy, cos(angle) * speed, sin(angle) * speed, 1f)
            }
        }

        override fun step() {
            frame++
            if (frame > totalFrames) isDone = true
        }

        override fun draw(canvas: Canvas) {
            // Cache per-frame invariant: life ratio is the same for every spark this frame.
            val lifeRatio = (1f - frame * invTotalFrames).coerceAtLeast(0f)
            val alpha = (230f * lifeRatio * lifeRatio).toInt().coerceIn(0, 255)
            paint.color = Palette.withAlpha(color, alpha)
            val drawRadius = (sparkBaseRadius * lifeRatio).coerceAtLeast(1f)

            for (s in sparks) {
                s.x += s.vx
                s.y += s.vy
                s.vy += gravity
                canvas.drawCircle(s.x, s.y, drawRadius, paint)
            }
        }
    }

    private class FireScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val grey: Int,
    ) : Effects.PersistentEffect {
        private val spikePaths: List<Path>
        private val emberPath: Path
        private val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        }
        private val emberPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private var frame = 0
        override val isDone = false

        init {
            val rng = Random(cx.toInt() xor cy.toInt())

            // Build starburst: 22 thin triangular spikes radiating from center.
            // Each spike is a narrow triangle: two base points very close to center
            // at ±halfWidth from the spike axis, tip at the outer radius.
            val spikeCount = 22
            // Pre-computed irregular length multipliers so the silhouette is organic.
            // Lengths alternate between longer and shorter with added per-spike noise.
            val lengthPattern = floatArrayOf(
                1.10f, 0.72f, 1.30f, 0.60f, 1.05f, 0.85f, 1.40f, 0.55f,
                1.20f, 0.68f, 1.35f, 0.78f, 1.15f, 0.62f, 1.25f, 0.90f,
                1.00f, 0.70f, 1.45f, 0.58f, 1.18f, 0.80f
            )
            spikePaths = List(spikeCount) { i ->
                val baseAngle = (i.toFloat() / spikeCount) * 2f * PI.toFloat() +
                        (rng.nextFloat() - 0.5f) * (2f * PI.toFloat() / spikeCount) * 0.6f
                val len = radius * lengthPattern[i] * (0.90f + rng.nextFloat() * 0.20f)
                // Half-angle of the spike's triangular cross-section — very narrow
                val halfWidth = radius * (0.055f + rng.nextFloat() * 0.035f)
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

            val spokeCount = 14
            emberPath = Path().apply {
                for (i in 0 until spokeCount) {
                    val angle = (i.toFloat() / spokeCount) * 2f * PI.toFloat() + (PI / spokeCount).toFloat()
                    val r = radius * (0.75f + rng.nextFloat() * 0.35f)
                    val px = cx + cos(angle) * r
                    val py = cy + sin(angle) * r
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }

            // Build the radial gradient once — cx, cy, radius, and colors are all
            // fixed at construction time and never change for a given scorch mark.
            fillPaint.shader = RadialGradient(
                cx, cy,
                radius * 1.45f,
                intArrayOf(Color.DKGRAY, primary, Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            // Starburst char mark: radial gradient from opaque black center to transparent tip.
            // The gradient + blur together ensure no hard edges anywhere on the spikes.
            // Gradient is pre-built in init — no per-frame allocation.
            for (path in spikePaths) {
                canvas.drawPath(path, fillPaint)
            }
        }
    }
}
