package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GalaxyTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {

    private class Star(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        var twinkleSeed: Float,
        var twinkleSpeed: Float
    )

    private val sparks = ArrayDeque<Star>()
    // Pre-allocated pool — avoids per-frame Star allocation and GC pressure
    private val pool = ArrayDeque<Star>().also { p -> repeat(120) { p.addLast(Star(0f, 0f, 0f, 0f, 0f, 0f, 0f)) } }
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val starPath = Path()

    private fun acquire(x: Float, y: Float, vx: Float, vy: Float, life: Float, seed: Float, speed: Float): Star {
        val s = if (pool.isNotEmpty()) pool.removeFirst() else Star(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        s.x = x; s.y = y; s.vx = vx; s.vy = vy; s.life = life; s.twinkleSeed = seed; s.twinkleSpeed = speed
        return s
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float) {
        starPath.reset()
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) outerR else innerR
            val px = cx + cos(STAR_ANGLES[i]) * r
            val py = cy + sin(STAR_ANGLES[i]) * r
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    override fun render(canvas: Canvas) {
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
        val cap = (100 * Settings.tailLengthMultiplier).toInt()
        while (sparks.size > cap) pool.addLast(sparks.removeFirst())

        val lifeDelta = 0.01f / Settings.tailLengthMultiplier
        val outerRBase = Settings.screenRatio * 0.32f
        val primaryColor = resolvedColors().primary
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx
            s.y += s.vy
            s.vx *= 0.97f
            s.vy *= 0.97f
            s.life -= lifeDelta
            if (s.life <= 0f) { it.remove(); pool.addLast(s); continue }
            val twinkle = 0.75f + 0.25f * sin(renderer.frame * s.twinkleSpeed + s.twinkleSeed * TAU)
            paint.color = Palette.withAlpha(primaryColor, (255f * s.life).toInt())
            val outerR = outerRBase * s.life * twinkle
            drawStar(canvas, s.x, s.y, outerR, outerR * 0.38f)
        }
    }

    override fun clear() {
        val it = sparks.iterator()
        while (it.hasNext()) { pool.addLast(it.next()); it.remove() }
    }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }

    companion object {
        private val TAU = (Math.PI * 2).toFloat()
        val STAR_ANGLES = FloatArray(8) { i -> (i * 45f - 90f) * Math.PI.toFloat() / 180f }
    }
}
