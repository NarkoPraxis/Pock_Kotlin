package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.random.Random

class FireTail(override val theme: ColorTheme) : TailRenderer {

    private class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        val isCore: Boolean
    )

    private val sparks = ArrayDeque<Spark>()
    private val maxSparks = 80
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val spawn = if (renderer.launched) 5 else 3
        repeat(spawn) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2
            val speed = Random.nextFloat() * 1.5f
            val dx = (Random.nextFloat() - 0.5f) * renderer.radius
            val dy = (Random.nextFloat() - 0.5f) * renderer.radius
            val isCore = kotlin.math.sqrt(dx * dx + dy * dy) < renderer.radius * 0.4f
            sparks.addLast(Spark(
                renderer.x + dx,
                renderer.y + dy,
                kotlin.math.cos(angle) * speed,
                kotlin.math.sin(angle) * speed - 0.4f,
                1f,
                isCore
            ))
        }
        while (sparks.size > (maxSparks * Settings.tailLengthMultiplier).toInt()) sparks.removeFirst()

        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx
            s.y += s.vy
            s.life -= 0.04f / Settings.tailLengthMultiplier
            if (s.life <= 0f) { it.remove(); continue }
            val t = 1f - s.life

            val c = Palette.lerpColor(theme.main.secondary, theme.main.primary, t)


            paint.color = Palette.withAlpha(c, (255f * s.life).toInt())
            val size = renderer.radius * 0.6f * s.life + Settings.screenRatio * 0.08f
            canvas.drawCircle(s.x, s.y, size, paint)
        }
    }

    override fun clear() { sparks.clear() }

    override fun fillTo(x: Float, y: Float) {
        sparks.forEach { it.x = x; it.y = y; it.vx = 0f; it.vy = 0f }
    }
}
