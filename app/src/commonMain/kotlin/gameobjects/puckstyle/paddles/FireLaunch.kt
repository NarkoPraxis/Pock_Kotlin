package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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

class FireLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

    // renderer.radius is effectively immutable after setup; cache radius-derived sizes
    // behind a cachedRadius guard instead of recomputing radius*const every frame.
    private var cachedRadius = -1f
    private var spawnJitter = 0f
    private var sparkBaseSize = 0f
    private var baseSize = 0f

    private fun ensureRadiusCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            spawnJitter = renderer.radius * 0.35f
            sparkBaseSize = renderer.radius * .32f
            baseSize = renderer.radius * .6f
        }
    }

    val SPAWN_JITTER: Float
        get() { ensureRadiusCache(); return spawnJitter }
    val SPARK_BASE_SIZE: Float
        get() { ensureRadiusCache(); return sparkBaseSize }
    val BASE_SIZE: Float
        get() { ensureRadiusCache(); return baseSize }

    private val tailSparks = ArrayDeque<Spark>()

    override fun drawChargingPaddle(scope: DrawScope) {
        updateAndDrawTail(scope, paddleX, paddleY)
        drawFireball(scope, paddleX, paddleY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawFireball(scope, cx, cy, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
    }

    // Spark pool — reuse freed Spark objects instead of allocating one per spawn each frame.
    private val tailSparkPool = ArrayDeque<Spark>()

    private fun obtainSpark(x: Float, y: Float, vx: Float, vy: Float, life: Float): Spark {
        val s = tailSparkPool.removeLastOrNull()
        if (s != null) {
            s.x = x; s.y = y; s.vx = vx; s.vy = vy; s.life = life
            return s
        }
        return Spark(x, y, vx, vy, life)
    }

    private fun updateAndDrawTail(scope: DrawScope, cx: Float, cy: Float) {
        ensureRadiusCache()
        val dx = cx - renderer.x
        val dy = cy - renderer.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = dx / dist; val ny = dy / dist

        repeat(2) {
            val speed = Random.nextFloat() * 1.2f + 0.4f
            val perpAmount = (Random.nextFloat() - 0.5f) * speed * 0.7f
            tailSparks.addLast(obtainSpark(
                cx + (Random.nextFloat() - 0.5f) * spawnJitter * 2f,
                cy + (Random.nextFloat() - 0.5f) * spawnJitter * 2f,
                nx * speed + (-ny) * perpAmount,
                ny * speed + nx * perpAmount,
                1f
            ))
        }
        while (tailSparks.size > 24) tailSparkPool.addLast(tailSparks.removeFirst())

        val primary = responsivePrimary
        val secondary = responsiveSecondary

        var i = 0
        while (i < tailSparks.size) {
            val s = tailSparks[i]
            s.x += s.vx; s.y += s.vy
            s.life -= 0.065f
            if (s.life <= 0f) { tailSparkPool.addLast(tailSparks.removeAt(i)); continue }
            val c = Palette.lerpColor(secondary, primary, 1f - s.life)
            val color = Palette.withAlpha(c, (220f * s.life).toInt().coerceIn(0, 255))
            scope.drawCircle(Color(color), (sparkBaseSize * s.life).coerceAtLeast(1f), Offset(s.x, s.y))
            i++
        }
    }

    private fun drawFireball(scope: DrawScope, cx: Float, cy: Float, ph: ChargePhase, fill: Float) {
        // animFrame follows the strobe clock in static UI, so the fireball keeps breathing and its
        // spark tail keeps streaming in place even though the paddle frame is frozen.
        ensureRadiusCache()
        val jitter = 1f + 0.08f * sin(animFrame * 0.9f)
        val outerR = baseSize * jitter
        scope.drawCircle(Color(responsiveSecondary), outerR, Offset(cx, cy))
        if (fill > 0f) {
            val coreColor = if (ph == ChargePhase.SweetSpot) renderer.invertedChargeColor(theme.shield.primary) else responsivePrimary
            val pulse = if (ph == ChargePhase.SweetSpot) 0.8f + 0.2f * sin(animFrame * 0.4f) else 1f
            scope.drawCircle(
                Color(Palette.withAlpha(coreColor, (255 * pulse).toInt().coerceIn(0, 255))),
                outerR * 0.6f * fill,
                Offset(cx, cy)
            )
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

        // Array (not List) so the per-frame draw() loop iterates by index without
        // allocating an Iterator each frame — this draw() runs in the `particles` section.
        private val sparks: Array<Spark>
        private var frame = 0
        private val totalFrames = 60
        private val invTotalFrames = 1f / totalFrames
        private val gravity = 0.04f * radius / Settings.screenRatio
        private val sparkBaseRadius = radius * 0.8f
        override var isDone = false
            private set

        init {
            val count = 28
            val angleRange = 2f * PI.toFloat()
            sparks = Array(count) { i ->
                val angle = (i.toFloat() / count) * angleRange + Random.nextFloat() * 0.4f
                val speed = radius * (0.12f + Random.nextFloat() * 0.18f)
                Spark(cx, cy, cos(angle) * speed, sin(angle) * speed, 1f)
            }
        }

        override fun step() {
            frame++
            if (frame > totalFrames) isDone = true
        }

        override fun draw(scope: DrawScope) {
            val lifeRatio = (1f - frame * invTotalFrames).coerceAtLeast(0f)
            val alpha = (230f * lifeRatio * lifeRatio).toInt().coerceIn(0, 255)
            val drawRadius = (sparkBaseRadius * lifeRatio).coerceAtLeast(1f)
            val drawColor = Color(Palette.withAlpha(color, alpha))
            for (idx in sparks.indices) {
                val s = sparks[idx]
                s.x += s.vx; s.y += s.vy
                s.vy += gravity
                scope.drawCircle(drawColor, drawRadius, Offset(s.x, s.y))
            }
        }
    }

    private class FireScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val grey: Int
    ) : Effects.PersistentEffect {
        // Array (not List) so the per-frame draw() loop iterates by index without
        // allocating an Iterator each frame — this draw() runs in the `particles` section.
        private val spikePaths: Array<Path>
        private val spikeBrush: Brush
        private var frame = 0
        override val isDone = false

        init {
            val rng = Random(cx.toInt() xor cy.toInt())
            val spikeCount = 22
            val lengthPattern = floatArrayOf(
                1.10f, 0.72f, 1.30f, 0.60f, 1.05f, 0.85f, 1.40f, 0.55f,
                1.20f, 0.68f, 1.35f, 0.78f, 1.15f, 0.62f, 1.25f, 0.90f,
                1.00f, 0.70f, 1.45f, 0.58f, 1.18f, 0.80f
            )
            spikePaths = Array(spikeCount) { i ->
                val baseAngle = (i.toFloat() / spikeCount) * 2f * PI.toFloat() +
                        (rng.nextFloat() - 0.5f) * (2f * PI.toFloat() / spikeCount) * 0.6f
                val len = radius * lengthPattern[i] * (0.90f + rng.nextFloat() * 0.20f)
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
            spikeBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0x77444444),
                    0.4f to Color(primary).copy(alpha = .3f),
                    1f to Color(Palette.TRANSPARENT)
                ),
                center = Offset(cx, cy),
                radius = radius * 1.45f
            )
        }

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            for (idx in spikePaths.indices) {
                scope.drawPath(spikePaths[idx], spikeBrush)
            }
        }
    }
}
