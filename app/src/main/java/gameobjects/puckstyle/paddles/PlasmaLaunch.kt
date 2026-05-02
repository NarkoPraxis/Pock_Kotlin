package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.withTranslation
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
import kotlin.random.Random

/** Mini plasma ball paddle: glowing core + animated lightning bolts; charge lerps background color. */
class PlasmaLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    // ── Paddle drawing ─────────────────────────────────────────────────────────

    private var lastColors = theme.main

    // arc.color is always WHITE — set once at construction
    private val arc = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var lastChargeRatio = -1f

    // Cached radius-derived values for drawBody
    private var cachedBodyRadius = -1f
    private var smallRadius = 0f   // renderer.radius * 0.2f
    private var medRadius = 0f     // renderer.radius * 0.7f
    private var largeRadius = 0f   // medRadius * 0.9f

    private fun ensureBodyCache() {
        if (cachedBodyRadius != renderer.radius) {
            cachedBodyRadius = renderer.radius
            smallRadius = renderer.radius * 0.2f
            medRadius = renderer.radius * 0.7f
            largeRadius = medRadius * 0.9f
            arc.strokeWidth = renderer.strokePaint.strokeWidth * 0.5f
        }
    }

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
        if (chargeFillRatio != lastChargeRatio || renderer.isInert || phase == ChargePhase.Inert) {
            fill.shader = createShader(radius)
            lastChargeRatio = chargeFillRatio
        }
    }

    fun createShader(radius: Float): Shader {
        val primary = if (phase == ChargePhase.Inert || renderer.isInert) theme.inert.primary else Palette.lerpColor(theme.main.primary, theme.shield.primary, chargeFillRatio)
        val secondary = if (phase == ChargePhase.Inert || renderer.isInert) theme.inert.secondary else Palette.lerpColor(theme.main.secondary, theme.shield.secondary, chargeFillRatio)

        return RadialGradient(0f, 0f, radius,
            intArrayOf(Color.WHITE, primary, secondary),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP)
    }

    fun drawBody(canvas: Canvas, cx: Float, cy: Float) {
        ensureBodyCache()
        ensureShader(medRadius)
        canvas.withTranslation(cx, cy) {
            drawCircle(0f, 0f, medRadius, fill)
            repeat(3) {
                val a1 = Random.nextFloat() * TWO_PI
                val a2 = a1 + (Random.nextFloat() - 0.5f) * 2
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
        internal val TWO_PI = PI.toFloat() * 2f

        fun spawnLighting(cx: Float, cy: Float, puckRadius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(PlasmaLightningBurst(cx, cy, puckRadius, primary, secondary))
        }

        fun spawnCelebration(cx: Float, cy: Float, puckRadius: Float, primary: Int, secondary: Int, highGoal: Boolean, fullCircle: Boolean) {
            Effects.addPersistentEffect(PlasmaLightningBurst(cx, cy, puckRadius, primary, secondary, highGoal = highGoal, fullCircle = fullCircle))
        }
    }

    // ── Persistent Effects ─────────────────────────────────────────────────────

    /**
     * Short-lived burst of jumping lightning bolts radiating from the impact point.
     * In fullCircle mode anchors scatter around center (persistent). In celebration
     * mode anchors sit at maxDistance in a semicircle and expire after totalFrames.
     */
    private class PlasmaLightningBurst(
        private val cx: Float,
        private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int,
        private val highGoal: Boolean = false,
        private val fullCircle: Boolean = true
    ) : Effects.PersistentEffect {

        private val boltPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            strokeWidth = Settings.strokeWidth * 0.7f   // constant across all instances
        }
        private val boltPath = Path()
        private var frame = 0
        private val totalFrames = 80
        private val totalFramesInv = 1f / totalFrames   // avoid per-frame division

        override val isDone: Boolean get() = !fullCircle && frame >= totalFrames



        private val anchors: Array<FloatArray>
        init {
            val rng = Random(cx.toLong() xor cy.toLong())
            anchors = if (fullCircle) {
                Array(20) {
                    val angle = rng.nextFloat() * TWO_PI
                    val dist = radius * (rng.nextFloat() * 3f)
                    floatArrayOf(cx + cos(angle) * dist, cy + sin(angle) * dist)
                }
            } else {
                val maxDist = radius * 5f
                val arcRange = PI.toFloat()
                val arcOffset = if (highGoal) 0f else PI.toFloat()
                Array(20) { i ->
                    val angle = arcOffset + (i.toFloat() / 20f) * arcRange + rng.nextFloat() * 0.3f
                    floatArrayOf(cx + cos(angle) * maxDist * (0.5f + rng.nextFloat() * 0.5f),
                                 cy + sin(angle) * maxDist * (0.5f + rng.nextFloat() * 0.5f))
                }
            }
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val life = (1f - frame.toFloat() * totalFramesInv).coerceAtLeast(0f)
            val alpha = (230f * life * life).toInt().coerceIn(0, 255)

            if (alpha > 0) {
                val rand = Random((frame / 3).toLong())
                val boltCount = (10 * life).toInt().coerceAtLeast(3)
                repeat(boltCount) {
                    val aIdx = rand.nextInt(anchors.size)
                    val bIdx = (aIdx + 1 + rand.nextInt(anchors.size - 1)) % anchors.size
                    val a = anchors[aIdx]; val b = anchors[bIdx]
                    boltPaint.color = Palette.withAlpha(if (it % 2 == 0) primary else secondary, alpha)
                    drawBolt(canvas, a[0], a[1], b[0], b[1], rand)
                }
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
            strokeWidth = Settings.strokeWidth * 0.38f   // constant; set once
        }
        private val boltPath = Path()

        // Color.rgb(15, 5, 20) is a constant — compute once
        private val scorchColor = Color.rgb(15, 5, 20)

        // Frozen bolt stubs baked at construction — fixed shape forever
        private data class BoltStub(
            val x1: Float, val y1: Float,
            val x2: Float, val y2: Float,
            val midX: Float, val midY: Float,
            val color: Int
        )
        private val stubs: List<BoltStub>

        init {
            val rng = Random(cx.toInt() xor cy.toInt())
            val count = 12
            stubs = List(count) { i ->
                val angle = (i.toFloat() / count) * TWO_PI + rng.nextFloat() * 0.4f
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
            scorchPaint.color = scorchColor
            scorchPaint.alpha = 170
            canvas.drawCircle(cx, cy, radius * 0.65f, scorchPaint)

            // Frozen bolt stubs at low alpha — charred traces
            for (stub in stubs) {
                burnBoltPaint.color = Palette.withAlpha(stub.color, 120)
                boltPath.reset()
                boltPath.moveTo(stub.x1, stub.y1)
                boltPath.lineTo(stub.midX, stub.midY)
                boltPath.lineTo(stub.x2, stub.y2)
                canvas.drawPath(boltPath, burnBoltPaint)
            }

            // Small bright center dot — residual plasma node
            // withAlpha(secondary, 160) already encodes alpha; no separate .alpha= needed
            scorchPaint.color = Palette.withAlpha(secondary, 160)
            canvas.drawCircle(cx, cy, radius * 0.18f, scorchPaint)
        }
    }

}
