package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.BallSize
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.random.Random

class FireTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {

    private class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        val isCore: Boolean
    )

    private val sparks = ArrayDeque<Spark>()
    private val maxSparks = 80
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    // Constants cached at construction — Settings.tailLengthMultiplier and screenRatio
    // do not change after initialization.
    private val maxCount = (maxSparks * Settings.tailLengthMultiplier).toInt()
    private val lifeDrain = 0.04f / Settings.tailLengthMultiplier
    private val baseParticleSize = Settings.screenRatio * 0.08f

    private val twoPi = (Math.PI * 2).toFloat()

    override fun render(canvas: Canvas) {
        val spawn = if (renderer.launched) 5 else 3
        val spawnRadius = renderer.r(BallSize.P040)
        val halfRadius = renderer.radius * 0.5f
        repeat(spawn) {
            val angle = Random.nextFloat() * twoPi
            val speed = Random.nextFloat() * 1.5f
            val dx = (Random.nextFloat() - 0.5f) * renderer.radius
            val dy = (Random.nextFloat() - 0.5f) * renderer.radius
            val isCore = kotlin.math.sqrt(dx * dx + dy * dy) < spawnRadius
            sparks.addLast(Spark(
                renderer.x + dx,
                renderer.y + dy,
                kotlin.math.cos(angle) * speed,
                kotlin.math.sin(angle) * speed - 0.4f,
                1f,
                isCore
            ))
        }
        while (sparks.size > maxCount) sparks.removeFirst()

        // Hoist loop-invariant color resolution — resolvedColors() must not be called per-spark.
        val colors = resolvedColors()
        val cSecondary = colors.secondary
        val cPrimary = colors.primary
        val particleBaseSize = renderer.r(BallSize.P060)

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
            paint.color = Palette.withAlpha(c, (255f * s.life).toInt())
            val size = particleBaseSize * s.life + baseParticleSize
            canvas.drawCircle(s.x, s.y, size, paint)
            i++
        }
    }

    override fun clear() { sparks.clear() }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }
}
