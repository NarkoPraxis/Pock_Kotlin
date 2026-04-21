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

class GalaxyTail(override val theme: ColorTheme) : TailRenderer {

    private class Star(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        val twinkleSeed: Float,
        val twinkleSpeed: Float
    )

    private val sparks = ArrayDeque<Star>()
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val starPath = Path()

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float) {
        starPath.reset()
        for (i in 0 until 8) {
            val angle = (i * 45f - 90f) * Math.PI.toFloat() / 180f
            val r = if (i % 2 == 0) outerR else innerR
            val px = cx + cos(angle) * r
            val py = cy + sin(angle) * r
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val spawn = if (renderer.launched) 4 else 2
        repeat(spawn) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2
            val speed = Random.nextFloat() * 1.2f
            sparks.addLast(Star(
                renderer.x + (Random.nextFloat() - 0.5f) * renderer.radius,
                renderer.y + (Random.nextFloat() - 0.5f) * renderer.radius,
                cos(angle) * speed,
                sin(angle) * speed,
                1f,
                Random.nextFloat(),
                0.157f + Random.nextFloat() * 0.157f
            ))
        }
        while (sparks.size > (100 * Settings.tailLengthMultiplier).toInt()) sparks.removeFirst()

        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx
            s.y += s.vy
            s.vx *= 0.97f
            s.vy *= 0.97f
            s.life -= 0.01f / Settings.tailLengthMultiplier
            if (s.life <= 0f) { it.remove(); continue }
            val twinkle = 0.75f + 0.25f * sin(renderer.frame * s.twinkleSpeed + s.twinkleSeed * Math.PI.toFloat() * 2f)
            paint.color = Palette.withAlpha(theme.primary, (255f * s.life).toInt())
            val outerR = Settings.screenRatio * 0.32f * s.life * twinkle
            drawStar(canvas, s.x, s.y, outerR, outerR * 0.38f)
        }
    }

    override fun clear() { sparks.clear() }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }
}
