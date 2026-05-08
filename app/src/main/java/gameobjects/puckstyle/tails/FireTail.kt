package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.random.Random

class FireTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        val isCore: Boolean
    )

    val SPAWN_RADIUS get() = renderer.radius * .04f
    val PARTICLE_BASE_SIZE get() = renderer.radius * .6f

    private val sparks = ArrayDeque<Spark>()
    private val maxSparks = 80

    // Constants cached at construction — Settings.tailLengthMultiplier and screenRatio
    // do not change after initialization.
    private val maxCount = (maxSparks * Settings.tailLengthMultiplier).toInt()
    private val lifeDrain = 0.04f / Settings.tailLengthMultiplier
    private val baseParticleSize = Settings.screenRatio * 0.08f

    private val twoPi = (Math.PI * 2).toFloat()

    override fun render(scope: DrawScope) {
        val spawn = if (renderer.launched) 5 else 3
        val halfRadius = renderer.radius * 0.5f
        repeat(spawn) {
            val angle = Random.nextFloat() * twoPi
            val speed = Random.nextFloat() * 1.5f
            val dx = (Random.nextFloat() - 0.5f) * renderer.radius
            val dy = (Random.nextFloat() - 0.5f) * renderer.radius
            val isCore = kotlin.math.sqrt(dx * dx + dy * dy) < SPAWN_RADIUS
            sparks.addLast(Spark(
                renderer.x + dx,
                renderer.y + dy,
                kotlin.math.cos(angle) * speed,
                kotlin.math.sin(angle) * speed,
                1f,
                isCore
            ))
        }
        while (sparks.size > maxCount) sparks.removeFirst()

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
                sparks.removeAt(i)
                continue
            }
            val t = 1f - s.life
            val c = Palette.lerpColor(cSecondary, cPrimary, t)
            val size = PARTICLE_BASE_SIZE * s.life + baseParticleSize
            scope.drawCircle(
                color = Color(Palette.withAlpha(c, (255f * s.life).toInt())),
                radius = size,
                center = Offset(s.x, s.y)
            )
            i++
        }
    }

    override fun clear() { sparks.clear() }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }
}
