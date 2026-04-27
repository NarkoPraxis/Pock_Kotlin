package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.withTranslation
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Mini plasma ball paddle: glowing core + animated lightning bolts; charge lerps background color. */
class PlasmaLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    // ── Paddle drawing ─────────────────────────────────────────────────────────

    private var lastColors = theme.main

    private val arc = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    private var lastChargeRatio = -1f

    override fun drawChargingPaddle(canvas: Canvas) =
        drawBody(canvas, paddleX, paddleY)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        drawBody(canvas, cx, cy)
    }

    protected fun ensureShader(radius: Float) {
        if (chargeFillRatio != lastChargeRatio) {
            fill.shader = createShader(radius)
            lastChargeRatio = chargeFillRatio
        }
    }

    fun createShader(radius: Float): Shader  {
        val primary = Palette.lerpColor(theme.main.primary, theme.shield.primary, chargeFillRatio)
        val secondary = Palette.lerpColor(theme.main.secondary, theme.shield.secondary, chargeFillRatio)

        return RadialGradient(0f, 0f, radius,
            intArrayOf(Color.WHITE, primary, secondary),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP)

    }

    fun drawBody(canvas: Canvas, cx: Float, cy: Float) {
        val smallRadius = renderer.radius * .2f
        val medRadius = renderer.radius * .7f
        val largeRadius = medRadius * .9f
        ensureShader(medRadius)
        canvas.withTranslation(cx, cy) {
            drawCircle(0f, 0f, medRadius, fill)
            arc.strokeWidth = renderer.strokePaint.strokeWidth * 0.5f
            repeat(3) {
                val a1 = Random.nextFloat() * Math.PI.toFloat() * 2
                val a2 = a1 + (Random.nextFloat() - 0.5f) * 2
                arc.color = Color.WHITE
                drawLine(
                    cos(a1) * smallRadius,
                    sin(a1) * smallRadius,
                    cos(a2) * largeRadius,
                    sin(a2) * largeRadius,
                    arc
                )
            }
            drawCircle(0f, 0f, medRadius, arc)
        }
    }

    // ── Residual ───────────────────────────────────────────────────────────────

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(
            PlasmaLightningBurst(rx, ry, renderer.radius, responsivePrimary, responsiveSecondary)
        )
    }

    companion object {
        fun spawnLighting(cx: Float, cy: Float, puckRadius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(PlasmaLightningBurst(cx, cy, puckRadius, primary, secondary))
        }
    }

    // ── Persistent Effects ─────────────────────────────────────────────────────

    /**
     * Short-lived burst of jumping lightning bolts radiating from the impact point.
     * Bolts jump between random anchor points each step, simulating static discharge.
     * Fades and completes after ~55 frames.
     */
    private class PlasmaLightningBurst(
        private val cx: Float,
        private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int
    ) : Effects.PersistentEffect {

        private val boltPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val boltPath = Path()
        private var frame = 0
        private val totalFrames = 55

        // Persistent — never expires on its own; cleared by score reset via Effects.onReset()
        override val isDone = false

        // 20 anchor points scattered around impact zone; bolts jump between random pairs
        private val anchors: Array<FloatArray>
        init {
            val rng = Random(cx.toLong() xor cy.toLong())
            anchors = Array(20) {
                val angle = rng.nextFloat() * 2f * PI.toFloat()
                val dist = radius * (rng.nextFloat() * 3f)
                floatArrayOf(cx + cos(angle) * dist, cy + sin(angle) * dist)
            }
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val life = (1f - frame.toFloat() / totalFrames).coerceAtLeast(0f)
            val alpha = (230f * life * life).toInt().coerceIn(0, 255)

            boltPaint.strokeWidth = Settings.strokeWidth * 0.55f

            // Animated bolts — skip once fully faded
            if (alpha > 0) {
                val rand = Random((frame / 3).toLong())
                val boltCount = (10 * life).toInt().coerceAtLeast(3)
                repeat(boltCount) {
                    val aIdx = rand.nextInt(anchors.size)
                    val bIdx = (aIdx + 1 + rand.nextInt(anchors.size - 1)) % anchors.size
                    val a = anchors[aIdx]
                    val b = anchors[bIdx]
                    val color = if (it % 2 == 0) primary else secondary
                    boltPaint.color = Palette.withAlpha(color, alpha)
                    drawBolt(canvas, a[0], a[1], b[0], b[1], rand)
                }
            }

            // First-frame bolts — fade with the animation but never below alpha 100
            val persistAlpha = alpha.coerceAtLeast(100)
            val firstRand = Random(0L)
            val firstBoltCount = 10 // (10 * life@frame0=1.0).coerceAtLeast(3)
            repeat(firstBoltCount) {
                val aIdx = firstRand.nextInt(anchors.size)
                val bIdx = (aIdx + 1 + firstRand.nextInt(anchors.size - 1)) % anchors.size
                val a = anchors[aIdx]
                val b = anchors[bIdx]
                val color = if (it % 2 == 0) primary else secondary
                boltPaint.color = Palette.withAlpha(color, persistAlpha)
                drawBolt(canvas, a[0], a[1], b[0], b[1], firstRand)
            }

            boltPaint.alpha = 255
        }

        private fun drawBolt(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, rand: Random) {
            val segments = 4
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val nx = -dy / len; val ny = dx / len
            boltPath.reset()
            boltPath.moveTo(x1, y1)
            for (i in 1 until segments) {
                val t = i.toFloat() / segments
                val mx = x1 + dx * t
                val my = y1 + dy * t
                val jag = radius * 2f * (rand.nextFloat() - 0.5f)
                boltPath.lineTo(mx + nx * jag, my + ny * jag)
            }
            boltPath.lineTo(x2, y2)
            canvas.drawPath(boltPath, boltPaint)
        }
    }

    /**
     * Permanent lightning burn — dark scorch ring + frozen bolt stubs radiating outward.
     * Never expires; cleared by onReset() when the next goal resets all persistent effects.
     */
    private class PlasmaBurn(
        private val cx: Float,
        private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int
    ) : Effects.PersistentEffect {

        override val isDone = false

        private val scorchPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val burnBoltPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val boltPath = Path()

        // Frozen bolt stubs baked at construction — fixed shape forever
        private data class BoltStub(
            val x1: Float, val y1: Float,
            val x2: Float, val y2: Float,
            val midX: Float, val midY: Float,  // one jag mid-point
            val color: Int
        )
        private val stubs: List<BoltStub>

        init {
            val rng = Random(cx.toInt() xor cy.toInt())
            val count = 12
            stubs = List(count) { i ->
                val angle = (i.toFloat() / count) * 2f * PI.toFloat() + rng.nextFloat() * 0.4f
                val reach = radius * (0.7f + rng.nextFloat() * 0.9f)
                val ex = cx + cos(angle) * reach
                val ey = cy + sin(angle) * reach
                val dx = ex - cx; val dy = ey - cy
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val nx = -dy / len; val ny = dx / len
                val jag = radius * 0.22f * (rng.nextFloat() - 0.5f)
                val mx = cx + dx * 0.55f + nx * jag
                val my = cy + dy * 0.55f + ny * jag
                val color = if (i % 2 == 0) primary else secondary
                BoltStub(cx, cy, ex, ey, mx, my, color)
            }
        }

        override fun step() { /* permanent — no decay */ }

        override fun draw(canvas: Canvas) {
            // Dark scorch circle
            scorchPaint.color = Color.rgb(15, 5, 20)
            scorchPaint.alpha = 170
            canvas.drawCircle(cx, cy, radius * 0.65f, scorchPaint)

            // Frozen bolt stubs at low alpha — charred traces
            burnBoltPaint.strokeWidth = Settings.strokeWidth * 0.38f
            for (stub in stubs) {
                burnBoltPaint.color = Palette.withAlpha(stub.color, 120)
                boltPath.reset()
                boltPath.moveTo(stub.x1, stub.y1)
                boltPath.lineTo(stub.midX, stub.midY)
                boltPath.lineTo(stub.x2, stub.y2)
                canvas.drawPath(boltPath, burnBoltPaint)
            }

            // Small bright center dot — residual plasma node
            scorchPaint.color = Palette.withAlpha(secondary, 160)
            scorchPaint.alpha = 160
            canvas.drawCircle(cx, cy, radius * 0.18f, scorchPaint)
        }
    }
}
