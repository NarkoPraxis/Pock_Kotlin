package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer

class IceTail(override val theme: ColorTheme) : TailRenderer {

    private class Shard(var x: Float, var y: Float, var size: Float, var life: Float)

    private val shards = ArrayDeque<Shard>()
    private val maxShards = 120
    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    private val coreColor = if (theme.isWarm) Color.rgb(255, 210, 220) else Color.rgb(210, 250, 245)
    private val midColor = if (theme.isWarm) Color.rgb(255, 170, 160) else Color.rgb(120, 220, 200)

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        shards.addLast(Shard(puck.x, puck.y, puck.radius * 1.0f, 1f))
        while (shards.size > maxShards) shards.removeFirst()

        val it = shards.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= 0.015f
            s.size *= 0.985f
            if (s.life <= 0f) { it.remove(); continue }
            val c = Palette.lerpColor(coreColor, midColor, 1f - s.life)
            paint.color = Palette.withAlpha(c, (220f * s.life).toInt())
            canvas.drawCircle(s.x, s.y, s.size, paint)
        }
    }

    override fun clear() { shards.clear() }
}
