package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer
import kotlin.random.Random

class GalaxyTail(override val theme: ColorTheme) : TailRenderer {

    private class Star(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val yellow: Boolean)

    private val sparks = ArrayDeque<Star>()
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val yellow = Color.rgb(255, 220, 80)

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        val spawn = if (launched) 4 else 2
        repeat(spawn) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2
            val speed = Random.nextFloat() * 1.2f
            sparks.addLast(Star(
                puck.x + (Random.nextFloat() - 0.5f) * puck.radius,
                puck.y + (Random.nextFloat() - 0.5f) * puck.radius,
                kotlin.math.cos(angle) * speed,
                kotlin.math.sin(angle) * speed,
                1f,
                Random.nextFloat() < 0.2f
            ))
        }
        while (sparks.size > 100) sparks.removeFirst()

        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx
            s.y += s.vy
            s.vx *= 0.97f
            s.vy *= 0.97f
            s.life -= 0.025f
            if (s.life <= 0f) { it.remove(); continue }
            val c = if (s.yellow) yellow else theme.primary
            paint.color = Palette.withAlpha(c, (255f * s.life).toInt())
            canvas.drawCircle(s.x, s.y, Settings.screenRatio * 0.12f * s.life, paint)
        }
    }

    override fun clear() { sparks.clear() }
}
