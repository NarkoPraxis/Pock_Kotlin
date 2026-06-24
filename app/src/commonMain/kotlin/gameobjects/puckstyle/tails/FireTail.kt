package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.random.Random

class FireTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        var isCore: Boolean
    )

    // renderer.radius is effectively immutable after setup; cache radius-derived sizes
    // behind a cachedRadius guard instead of recomputing radius*const every frame.
    private var cachedRadius = -1f
    private var spawnRadius = 0f
    private var particleBaseSize = 0f

    val SPAWN_RADIUS: Float
        get() { ensureRadiusCache(); return spawnRadius }
    val PARTICLE_BASE_SIZE: Float
        get() { ensureRadiusCache(); return particleBaseSize }

    private fun ensureRadiusCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            spawnRadius = renderer.radius * .04f
            particleBaseSize = renderer.radius * .6f
        }
    }

    private val sparks = ArrayDeque<Spark>()
    private val maxSparks = 80

    // Constants cached at construction — Settings.tailLengthMultiplier and screenRatio
    // do not change after initialization.
    private val maxCount = (maxSparks * Settings.tailLengthMultiplier).toInt()
    private val lifeDrain = 0.04f / Settings.tailLengthMultiplier
    private val baseParticleSize = Settings.screenRatio * 0.08f

    private val twoPi = (PI * 2).toFloat()

    // Spark pool — reuse freed Spark objects instead of allocating a new one each spawn.
    private val sparkPool = ArrayDeque<Spark>()

    private fun obtainSpark(x: Float, y: Float, vx: Float, vy: Float, life: Float, isCore: Boolean): Spark {
        val s = sparkPool.removeLastOrNull()
        if (s != null) {
            s.x = x; s.y = y; s.vx = vx; s.vy = vy; s.life = life; s.isCore = isCore
            return s
        }
        return Spark(x, y, vx, vy, life, isCore)
    }

    override fun render(scope: DrawScope) {
        if (renderer.staticUiMode) { renderStatic(scope); return }
        ensureRadiusCache()
        val spawn = if (renderer.launched) 5 else 3
        repeat(spawn) {
            val angle = Random.nextFloat() * twoPi
            val speed = Random.nextFloat() * 1.5f
            val dx = (Random.nextFloat() - 0.5f) * renderer.radius
            val dy = (Random.nextFloat() - 0.5f) * renderer.radius
            val isCore = kotlin.math.sqrt(dx * dx + dy * dy) < spawnRadius
            sparks.addLast(obtainSpark(
                renderer.x + dx,
                renderer.y + dy,
                kotlin.math.cos(angle) * speed,
                kotlin.math.sin(angle) * speed,
                1f,
                isCore
            ))
        }
        while (sparks.size > maxCount) sparkPool.addLast(sparks.removeFirst())

        // Hoist loop-invariant color resolution — resolvedColors() must not be called per-spark.
        val colors = responsiveGroup
        val cSecondary = colors.secondary
        val cPrimary = colors.primary

        var i = 0
        while (i < sparks.size) {
            val s = sparks[i]
            s.x += s.vx
            s.y += s.vy
            s.life -= lifeDrain
            if (s.life <= 0f) {
                sparkPool.addLast(sparks.removeAt(i))
                continue
            }
            val t = 1f - s.life
            val c = Palette.lerpColor(cSecondary, cPrimary, t)
            val size = particleBaseSize * s.life + baseParticleSize
            scope.drawCircle(
                color = Color(Palette.withAlpha(c, (255f * s.life).toInt())),
                radius = size,
                center = Offset(s.x, s.y)
            )
            i++
        }
    }

    // Per-index jitter fractions for the static screenshot. These derive only from the
    // index (formerly `Random(i + 1).nextFloat()`), so they are deterministic and can be
    // precomputed once instead of allocating a fresh Random per particle every frame.
    private var staticJitterCount = -1
    private var staticJitterX: FloatArray = FloatArray(0)
    private var staticJitterY: FloatArray = FloatArray(0)

    private fun ensureStaticJitter(count: Int) {
        if (staticJitterCount == count) return
        staticJitterCount = count
        staticJitterX = FloatArray(count)
        staticJitterY = FloatArray(count)
        for (i in 0 until count) {
            // Same sequence as the former `Random(i + 1)` per particle: first nextFloat() -> X,
            // second nextFloat() -> Y. Reproduces identical pixels.
            val rnd = Random(i + 1)
            staticJitterX[i] = rnd.nextFloat() - 0.5f
            staticJitterY[i] = rnd.nextFloat() - 0.5f
        }
    }

    /** Frozen "screenshot of motion": embers strewn along the swoosh, hot at the ball, fading to the tip. */
    private fun renderStatic(scope: DrawScope) {
        ensureRadiusCache()
        val colors = responsiveGroup
        val count = maxCount.coerceIn(18, 70)
        ensureStaticJitter(count)
        val last = (count - 1).coerceAtLeast(1)
        val jitter = renderer.radius * 0.6f
        // Embers hold their swoosh positions but flicker over the strobe clock, so the static
        // screenshot reads as fire burning in place rather than a frozen frame.
        val clock = renderer.strobe.toFloat()
        for (i in 0 until count) {
            val ratio = i.toFloat() / last
            val base = staticSwooshPoint(ratio)
            val jx = staticJitterX[i] * jitter
            val jy = staticJitterY[i] * jitter
            val flicker = 0.7f + 0.3f * kotlin.math.sin(clock * 0.18f + i * 0.7f)
            val life = (1f - ratio) * flicker
            val c = Palette.lerpColor(colors.secondary, colors.primary, ratio)
            val size = particleBaseSize * life + baseParticleSize
            scope.drawCircle(
                color = Color(Palette.withAlpha(c, (255f * life).toInt().coerceIn(0, 255))),
                radius = size,
                center = Offset(base.x + jx, base.y + jy)
            )
        }
    }

    override fun clear() { sparks.clear() }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }
}
