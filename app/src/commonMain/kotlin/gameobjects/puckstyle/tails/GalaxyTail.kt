package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GalaxyTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Star(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        var twinkleSeed: Float,
        var twinkleSpeed: Float
    )

    private val sparks = ArrayDeque<Star>()
    private val pool = ArrayDeque<Star>().also { p -> repeat(120) { p.addLast(Star(0f, 0f, 0f, 0f, 0f, 0f, 0f)) } }
    private val starPath = Path()

    private val cap = (100 * Settings.tailLengthMultiplier).toInt()
    private val lifeDelta = 0.01f / Settings.tailLengthMultiplier
    private val outerRBase = Settings.screenRatio * 0.32f

    private fun acquire(x: Float, y: Float, vx: Float, vy: Float, life: Float, seed: Float, speed: Float): Star {
        val s = if (pool.isNotEmpty()) pool.removeFirst() else Star(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        s.x = x; s.y = y; s.vx = vx; s.vy = vy; s.life = life
        s.twinkleSeed = seed * TAU
        s.twinkleSpeed = speed
        return s
    }

    override fun render(scope: DrawScope) {
        val spawn = if (renderer.launched) 4 else 2
        repeat(spawn) {
            val angle = Random.nextFloat() * TAU
            val speed = Random.nextFloat() * 1.2f
            sparks.addLast(acquire(
                renderer.x + (Random.nextFloat() - 0.5f) * renderer.radius,
                renderer.y + (Random.nextFloat() - 0.5f) * renderer.radius,
                cos(angle) * speed,
                sin(angle) * speed,
                1f,
                Random.nextFloat(),
                0.157f + Random.nextFloat() * 0.157f
            ))
        }
        while (sparks.size > cap) pool.addLast(sparks.removeFirst())

        val primaryColor = responsivePrimary
        val frameF = renderer.frame.toFloat()

        var i = 0
        while (i < sparks.size) {
            val s = sparks[i]
            s.x += s.vx
            s.y += s.vy
            s.vx *= 0.97f
            s.vy *= 0.97f
            s.life -= lifeDelta
            if (s.life <= 0f) {
                sparks.removeAt(i)
                pool.addLast(s)
                continue
            }
            val twinkle = 0.75f + 0.25f * sin(frameF * s.twinkleSpeed + s.twinkleSeed)
            val alpha = (255f * s.life).toInt()
            val color = Palette.withAlpha(primaryColor, alpha)
            val outerR = outerRBase * s.life * twinkle
            val innerR = outerR * 0.38f
            val sx = s.x
            val sy = s.y

            starPath.reset()
            for (k in 0 until 8) {
                val r = if (k % 2 == 0) outerR else innerR
                val px = sx + cos(STAR_ANGLES[k]) * r
                val py = sy + sin(STAR_ANGLES[k]) * r
                if (k == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
            }
            starPath.close()
            scope.drawPath(starPath, Color(color))
            i++
        }
    }

    override fun clear() {
        while (sparks.isNotEmpty()) pool.addLast(sparks.removeFirst())
    }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }

    companion object {
        private val TAU = (PI * 2).toFloat()
        val STAR_ANGLES = FloatArray(8) { i -> (i * 45f - 90f) * PI.toFloat() / 180f }
    }
}
